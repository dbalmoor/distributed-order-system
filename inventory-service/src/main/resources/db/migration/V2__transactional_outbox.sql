CREATE TABLE outbox (
    message_id UUID PRIMARY KEY,
    aggregate_type VARCHAR(64) NOT NULL,
    aggregate_id TEXT NOT NULL,
    event_type VARCHAR(128) NOT NULL,
    topic VARCHAR(255) NOT NULL,
    payload JSONB NOT NULL,
    headers JSONB NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'NEW',
    attempt_count INTEGER NOT NULL DEFAULT 0,
    lease_until TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT clock_timestamp(),
    published_at TIMESTAMP WITH TIME ZONE,
    last_error TEXT,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_outbox_status_created_at ON outbox (status, created_at);
CREATE INDEX idx_outbox_aggregate_created_at ON outbox (aggregate_type, aggregate_id, created_at);
