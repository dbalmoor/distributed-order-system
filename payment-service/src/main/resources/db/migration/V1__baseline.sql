CREATE TABLE IF NOT EXISTS payments (
    id BIGSERIAL PRIMARY KEY,
    order_id BIGINT,
    order_number VARCHAR(255),
    amount NUMERIC(15,2),
    status VARCHAR(255),
    created_at TIMESTAMP
);
