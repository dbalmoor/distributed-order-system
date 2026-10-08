ALTER TABLE payments
    ADD COLUMN saga_id VARCHAR(36),
    ADD COLUMN type VARCHAR(16) NOT NULL DEFAULT 'CHARGE';

UPDATE payments
SET saga_id = gen_random_uuid()::text
WHERE saga_id IS NULL;

ALTER TABLE payments
    ALTER COLUMN saga_id SET NOT NULL;

ALTER TABLE payments
    ADD CONSTRAINT uk_payments_saga_id_type UNIQUE (saga_id, type);
