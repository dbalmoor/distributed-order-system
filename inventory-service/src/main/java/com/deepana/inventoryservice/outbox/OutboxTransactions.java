package com.deepana.inventoryservice.outbox;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class OutboxTransactions {

    private final JdbcTemplate jdbcTemplate;

    @Transactional
    public List<OutboxMessage> claim(int batchSize, long leaseMillis) {
        List<OutboxMessage> messages = jdbcTemplate.query("""
                SELECT o.message_id, o.aggregate_type, o.aggregate_id, o.event_type,
                       o.topic, o.payload::text, o.headers::text
                FROM outbox o
                WHERE (o.status IN ('NEW', 'FAILED')
                       OR (o.status = 'IN_PROGRESS' AND o.lease_until <= now()))
                  AND NOT EXISTS (
                      SELECT 1
                      FROM outbox earlier
                      WHERE earlier.aggregate_type = o.aggregate_type
                        AND earlier.aggregate_id = o.aggregate_id
                        AND earlier.published_at IS NULL
                        AND (earlier.created_at, earlier.message_id) < (o.created_at, o.message_id)
                  )
                ORDER BY o.created_at, o.message_id
                LIMIT ?
                FOR UPDATE OF o SKIP LOCKED
                """,
                (rs, row) -> new OutboxMessage(
                        rs.getObject("message_id", UUID.class),
                        rs.getString("aggregate_type"),
                        rs.getString("aggregate_id"),
                        rs.getString("event_type"),
                        rs.getString("topic"),
                        rs.getString("payload"),
                        rs.getString("headers")),
                batchSize);
        for (OutboxMessage message : messages) {
            jdbcTemplate.update("""
                    UPDATE outbox
                    SET status = 'IN_PROGRESS',
                        lease_until = now() + (? * interval '1 millisecond'),
                        version = version + 1
                    WHERE message_id = ?
                    """, leaseMillis, message.messageId());
        }
        return messages;
    }

    @Transactional
    public int resetExpiredLeases() {
        return jdbcTemplate.update("""
                UPDATE outbox
                SET status = 'FAILED', lease_until = NULL,
                    last_error = COALESCE(last_error, 'Publish lease expired'),
                    version = version + 1
                WHERE status = 'IN_PROGRESS' AND lease_until <= now()
                """);
    }

    @Transactional
    public void markPublished(UUID messageId) {
        int updated = jdbcTemplate.update("""
                UPDATE outbox
                SET status = 'PUBLISHED', published_at = now(), lease_until = NULL,
                    last_error = NULL, version = version + 1
                WHERE message_id = ? AND status = 'IN_PROGRESS'
                """, messageId);
        if (updated != 1) {
            throw new IllegalStateException("Outbox row is no longer claimed: " + messageId);
        }
    }

    @Transactional
    public void markFailed(UUID messageId, String error) {
        int updated = jdbcTemplate.update("""
                UPDATE outbox
                SET status = 'FAILED', attempt_count = attempt_count + 1,
                    lease_until = NULL, last_error = ?, version = version + 1
                WHERE message_id = ? AND status = 'IN_PROGRESS'
                """, error, messageId);
        if (updated != 1) {
            throw new IllegalStateException("Outbox row is no longer claimed: " + messageId);
        }
    }
}
