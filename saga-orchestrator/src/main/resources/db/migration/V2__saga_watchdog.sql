ALTER TABLE saga_instance
    ADD COLUMN last_heartbeat_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT clock_timestamp(),
    ADD COLUMN retry_count INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN needs_attention BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE saga_instance
SET last_heartbeat_at = updated_at;

CREATE INDEX idx_saga_instance_watchdog
    ON saga_instance (deadline_at)
    WHERE status IN ('ACTIVE', 'COMPENSATING') AND needs_attention = FALSE;
