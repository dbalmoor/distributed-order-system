package com.deepana.sagaorchestrator.repository;

import com.deepana.sagaorchestrator.entity.SagaSnapshot;
import com.deepana.sagaorchestrator.entity.SagaStatus;
import com.deepana.sagaorchestrator.entity.SagaStep;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class SagaRepository {

    private final JdbcTemplate jdbcTemplate;

    public boolean insertSagaIfAbsent(String sagaId, Long orderId, SagaStatus status,
                                      SagaStep step, long deadlineMillis) {
        return jdbcTemplate.update("""
                INSERT INTO saga_instance
                    (saga_id, order_id, status, current_step, deadline_at,
                     last_heartbeat_at, started_at, updated_at)
                VALUES (?, ?, ?, ?, now() + (? * interval '1 millisecond'), now(), now(), now())
                ON CONFLICT (order_id) DO NOTHING
                """, sagaId, orderId, status.name(), step.name(), deadlineMillis) == 1;
    }

    public Optional<SagaSnapshot> findBySagaIdForUpdate(String sagaId) {
        return jdbcTemplate.query("""
                SELECT saga_id, order_id, status, current_step, version, deadline_at,
                       last_heartbeat_at, retry_count, needs_attention
                FROM saga_instance WHERE saga_id = ? FOR UPDATE
                """, (rs, row) -> snapshot(rs),
                sagaId).stream().findFirst();
    }

    public Optional<SagaSnapshot> findByOrderIdForUpdate(Long orderId) {
        return jdbcTemplate.query("""
                SELECT saga_id, order_id, status, current_step, version, deadline_at,
                       last_heartbeat_at, retry_count, needs_attention
                FROM saga_instance WHERE order_id = ? FOR UPDATE
                """, (rs, row) -> snapshot(rs),
                orderId).stream().findFirst();
    }

    public List<SagaSnapshot> findExpiredForUpdate() {
        return jdbcTemplate.query("""
                SELECT saga_id, order_id, status, current_step, version, deadline_at,
                       last_heartbeat_at, retry_count, needs_attention
                FROM saga_instance
                WHERE status IN ('ACTIVE', 'COMPENSATING')
                  AND needs_attention = FALSE
                  AND deadline_at < now()
                ORDER BY deadline_at, id
                LIMIT 100
                FOR UPDATE SKIP LOCKED
                """, (rs, row) -> snapshot(rs));
    }

    public List<SagaAdminView> findNeedingAttention() {
        return jdbcTemplate.query("""
                SELECT saga_id, order_id, current_step, retry_count, deadline_at,
                       last_heartbeat_at
                FROM saga_instance
                WHERE status = 'NEEDS_ATTENTION' AND needs_attention = TRUE
                ORDER BY updated_at, id
                """, (rs, row) -> new SagaAdminView(
                rs.getString("saga_id"),
                rs.getLong("order_id"),
                SagaStep.valueOf(rs.getString("current_step")),
                rs.getInt("retry_count"),
                rs.getTimestamp("deadline_at") == null ? null : rs.getTimestamp("deadline_at").toInstant(),
                rs.getTimestamp("last_heartbeat_at").toInstant()));
    }

    public void updateState(String sagaId, SagaStatus status, SagaStep step, long deadlineMillis) {
        int updated = jdbcTemplate.update("""
                UPDATE saga_instance
                SET status = ?, current_step = ?,
                    deadline_at = CASE WHEN ? THEN now() + (? * interval '1 millisecond') ELSE NULL END,
                    last_heartbeat_at = now(), retry_count = 0, needs_attention = ?,
                    updated_at = now(), version = version + 1
                WHERE saga_id = ?
                """,
                status.name(), step.name(), isActive(status), deadlineMillis,
                status == SagaStatus.NEEDS_ATTENTION, sagaId);
        if (updated != 1) {
            throw new IllegalStateException("Saga disappeared during transition: " + sagaId);
        }
    }

    public void scheduleRetry(String sagaId, long deadlineMillis) {
        updateRetry(sagaId, deadlineMillis, true);
    }

    public void touchDeadline(String sagaId, long deadlineMillis) {
        updateRetry(sagaId, deadlineMillis, false);
    }

    public void markNeedsAttention(String sagaId) {
        int updated = jdbcTemplate.update("""
                UPDATE saga_instance
                SET status = 'NEEDS_ATTENTION', needs_attention = TRUE, deadline_at = NULL,
                    last_heartbeat_at = now(), updated_at = now(), version = version + 1
                WHERE saga_id = ? AND status IN ('ACTIVE', 'COMPENSATING')
                """, sagaId);
        if (updated != 1) {
            throw new IllegalStateException("Saga is no longer eligible for attention: " + sagaId);
        }
    }

    public boolean markMessageProcessed(String consumerName, UUID messageId) {
        return jdbcTemplate.update("""
                INSERT INTO processed_message (consumer_name, message_id)
                VALUES (?, ?)
                ON CONFLICT (consumer_name, message_id) DO NOTHING
                """, consumerName, messageId) == 1;
    }

    public void appendStepLog(String sagaId, SagaStep step, String eventType,
                              SagaStatus fromState, SagaStatus toState, String payload) {
        jdbcTemplate.update("""
                INSERT INTO saga_step_log
                    (saga_id, step_name, event_type, from_state, to_state, payload)
                VALUES (?, ?, ?, ?, ?, CAST(? AS jsonb))
                """, sagaId, step.name(), eventType,
                fromState == null ? null : fromState.name(),
                toState == null ? null : toState.name(),
                payload);
    }

    public boolean hasStepEvent(String sagaId, String eventType) {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject("""
                SELECT EXISTS (
                    SELECT 1 FROM saga_step_log WHERE saga_id = ? AND event_type = ?
                )
                """, Boolean.class, sagaId, eventType));
    }

    public Optional<String> findOrderCreatedPayload(String sagaId) {
        return jdbcTemplate.query("""
                SELECT payload::text
                FROM saga_step_log
                WHERE saga_id = ? AND event_type = 'order.created'
                ORDER BY id
                LIMIT 1
                """, rs -> rs.next() ? Optional.ofNullable(rs.getString(1)) : Optional.empty(), sagaId);
    }

    private boolean isActive(SagaStatus status) {
        return status == SagaStatus.ACTIVE || status == SagaStatus.COMPENSATING;
    }

    private void updateRetry(String sagaId, long deadlineMillis, boolean increment) {
        int updated = jdbcTemplate.update("""
                UPDATE saga_instance
                SET deadline_at = now() + (? * interval '1 millisecond'),
                    retry_count = retry_count + ?, last_heartbeat_at = now(),
                    updated_at = now(), version = version + 1
                WHERE saga_id = ? AND status IN ('ACTIVE', 'COMPENSATING')
                """, deadlineMillis, increment ? 1 : 0, sagaId);
        if (updated != 1) {
            throw new IllegalStateException("Saga disappeared during retry scheduling: " + sagaId);
        }
    }

    private SagaSnapshot snapshot(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new SagaSnapshot(
                rs.getString("saga_id"),
                rs.getLong("order_id"),
                SagaStatus.valueOf(rs.getString("status")),
                SagaStep.valueOf(rs.getString("current_step")),
                rs.getLong("version"),
                rs.getTimestamp("deadline_at") == null ? null : rs.getTimestamp("deadline_at").toInstant(),
                rs.getTimestamp("last_heartbeat_at").toInstant(),
                rs.getInt("retry_count"),
                rs.getBoolean("needs_attention"));
    }
}
