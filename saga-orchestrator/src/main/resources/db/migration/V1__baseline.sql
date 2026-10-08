CREATE TABLE saga_instance (
    id BIGSERIAL PRIMARY KEY,
    saga_id VARCHAR(36) NOT NULL UNIQUE,
    order_id BIGINT NOT NULL UNIQUE,
    status VARCHAR(32) NOT NULL,
    current_step VARCHAR(64) NOT NULL,
    deadline_at TIMESTAMP WITH TIME ZONE,
    started_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE saga_step_log (
    id BIGSERIAL PRIMARY KEY,
    saga_id VARCHAR(36) NOT NULL,
    step_name VARCHAR(64) NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    from_state VARCHAR(32),
    to_state VARCHAR(32),
    payload JSONB,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT clock_timestamp()
);

CREATE INDEX idx_saga_step_log_saga_step ON saga_step_log (saga_id, step_name);
CREATE INDEX idx_saga_step_log_saga_event ON saga_step_log (saga_id, event_type);

CREATE TABLE processed_message (
    consumer_name TEXT NOT NULL,
    message_id UUID NOT NULL,
    processed_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    PRIMARY KEY (consumer_name, message_id)
);

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
