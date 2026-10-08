package com.deepana.sagaorchestrator.repository;

import com.deepana.sagaorchestrator.entity.SagaSnapshot;
import com.deepana.sagaorchestrator.entity.SagaStatus;
import com.deepana.sagaorchestrator.entity.SagaStep;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class SagaRepository {

    private final JdbcTemplate jdbcTemplate;

    public boolean insertSagaIfAbsent(String sagaId, Long orderId, SagaStatus status,
                                      SagaStep step, long deadlineMillis) {
        return jdbcTemplate.update("""
                INSERT INTO saga_instance
                    (saga_id, order_id, status, current_step, deadline_at, started_at, updated_at)
                VALUES (?, ?, ?, ?, now() + (? * interval '1 millisecond'), now(), now())
                ON CONFLICT (order_id) DO NOTHING
                """, sagaId, orderId, status.name(), step.name(), deadlineMillis) == 1;
    }

    public Optional<SagaSnapshot> findBySagaIdForUpdate(String sagaId) {
        return jdbcTemplate.query("""
                SELECT saga_id, order_id, status, current_step, version
                FROM saga_instance WHERE saga_id = ? FOR UPDATE
                """, (rs, row) -> new SagaSnapshot(
                        rs.getString("saga_id"),
                        rs.getLong("order_id"),
                        SagaStatus.valueOf(rs.getString("status")),
                        SagaStep.valueOf(rs.getString("current_step")),
                        rs.getLong("version")),
                sagaId).stream().findFirst();
    }

    public Optional<SagaSnapshot> findByOrderIdForUpdate(Long orderId) {
        return jdbcTemplate.query("""
                SELECT saga_id, order_id, status, current_step, version
                FROM saga_instance WHERE order_id = ? FOR UPDATE
                """, (rs, row) -> new SagaSnapshot(
                        rs.getString("saga_id"),
                        rs.getLong("order_id"),
                        SagaStatus.valueOf(rs.getString("status")),
                        SagaStep.valueOf(rs.getString("current_step")),
                        rs.getLong("version")),
                orderId).stream().findFirst();
    }

    public void updateState(String sagaId, SagaStatus status, SagaStep step, long deadlineMillis) {
        int updated = jdbcTemplate.update("""
                UPDATE saga_instance
                SET status = ?, current_step = ?,
                    deadline_at = CASE WHEN ? THEN now() + (? * interval '1 millisecond') ELSE NULL END,
                    updated_at = now(), version = version + 1
                WHERE saga_id = ?
                """,
                status.name(), step.name(), isActive(status), deadlineMillis, sagaId);
        if (updated != 1) {
            throw new IllegalStateException("Saga disappeared during transition: " + sagaId);
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

    private boolean isActive(SagaStatus status) {
        return status == SagaStatus.ACTIVE || status == SagaStatus.COMPENSATING;
    }
}
