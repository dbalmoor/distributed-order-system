CREATE TABLE IF NOT EXISTS orders (
    id BIGSERIAL PRIMARY KEY,
    order_number VARCHAR(255) NOT NULL UNIQUE,
    user_id BIGINT,
    total_amount NUMERIC(19,2),
    status VARCHAR(255),
    payment_type VARCHAR(255),
    fulfillment_type VARCHAR(255),
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    payment_pending BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE TABLE IF NOT EXISTS order_items (
    id BIGSERIAL PRIMARY KEY,
    product_id BIGINT,
    quantity INTEGER,
    price NUMERIC(19,2),
    order_id BIGINT,
    CONSTRAINT fk_order_items_order FOREIGN KEY (order_id) REFERENCES orders(id)
);

CREATE TABLE IF NOT EXISTS outbox_events (
    id BIGSERIAL PRIMARY KEY,
    aggregate_type VARCHAR(255),
    aggregate_id VARCHAR(255),
    event_type VARCHAR(255),
    payload TEXT,
    saga_id VARCHAR(255),
    trace_id VARCHAR(255),
    status VARCHAR(255),
    created_at TIMESTAMP,
    processed_at TIMESTAMP
);
