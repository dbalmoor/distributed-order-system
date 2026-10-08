CREATE TABLE IF NOT EXISTS inventory (
    id BIGSERIAL PRIMARY KEY,
    product_id BIGINT NOT NULL UNIQUE,
    available_qty INTEGER NOT NULL,
    reserved_qty INTEGER NOT NULL,
    version BIGINT
);

CREATE TABLE IF NOT EXISTS processed_inventory_events (
    id BIGSERIAL PRIMARY KEY,
    saga_id VARCHAR(255),
    order_id BIGINT,
    event_type VARCHAR(255),
    processed_at TIMESTAMP
);
