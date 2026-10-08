UPDATE processed_inventory_events
SET event_type = 'RELEASED'
WHERE event_type = 'RELEASE';

DELETE FROM processed_inventory_events
WHERE saga_id IS NULL OR event_type IS NULL;

DELETE FROM processed_inventory_events older
USING processed_inventory_events newer
WHERE older.saga_id = newer.saga_id
  AND older.event_type = newer.event_type
  AND older.id > newer.id;

ALTER TABLE processed_inventory_events
    ALTER COLUMN saga_id SET NOT NULL,
    ALTER COLUMN event_type SET NOT NULL;

ALTER TABLE processed_inventory_events
    ADD CONSTRAINT uk_processed_inventory_events_saga_event
    UNIQUE (saga_id, event_type);
