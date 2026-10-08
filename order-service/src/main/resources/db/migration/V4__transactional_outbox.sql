ALTER TABLE outbox_events RENAME TO outbox;
ALTER TABLE outbox RENAME COLUMN id TO legacy_id;
ALTER TABLE outbox RENAME COLUMN created_at TO legacy_created_at;
ALTER TABLE outbox RENAME COLUMN processed_at TO legacy_published_at;
ALTER TABLE outbox RENAME COLUMN payload TO legacy_payload;

ALTER TABLE outbox
    ADD COLUMN message_id UUID,
    ADD COLUMN topic VARCHAR(255),
    ADD COLUMN payload JSONB,
    ADD COLUMN headers JSONB,
    ADD COLUMN attempt_count INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN lease_until TIMESTAMP WITH TIME ZONE,
    ADD COLUMN published_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN last_error TEXT,
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

UPDATE outbox
SET message_id = gen_random_uuid(),
    aggregate_type = COALESCE(NULLIF(aggregate_type, ''), 'ORDER'),
    aggregate_id = COALESCE(aggregate_id, ''),
    event_type = COALESCE(NULLIF(event_type, ''), 'UNKNOWN'),
    topic = COALESCE(NULLIF(event_type, ''), 'UNKNOWN'),
    payload = CASE
        WHEN legacy_payload IS NULL OR btrim(legacy_payload) = '' THEN '{}'::jsonb
        ELSE legacy_payload::jsonb
    END,
    headers = jsonb_strip_nulls(jsonb_build_object(
        'sagaId', saga_id,
        'traceId', trace_id,
        'eventType', COALESCE(NULLIF(event_type, ''), 'UNKNOWN')
    )),
    status = CASE WHEN upper(status) IN ('SENT', 'PUBLISHED') THEN 'PUBLISHED' ELSE 'NEW' END,
    legacy_created_at = COALESCE(legacy_created_at, now()),
    published_at = CASE
        WHEN upper(status) IN ('SENT', 'PUBLISHED') THEN legacy_published_at
        ELSE NULL
    END;

ALTER TABLE outbox
    ALTER COLUMN message_id SET NOT NULL,
    ALTER COLUMN aggregate_type SET NOT NULL,
    ALTER COLUMN aggregate_id SET NOT NULL,
    ALTER COLUMN event_type SET NOT NULL,
    ALTER COLUMN topic SET NOT NULL,
    ALTER COLUMN payload SET NOT NULL,
    ALTER COLUMN headers SET NOT NULL,
    ALTER COLUMN status SET DEFAULT 'NEW',
    ALTER COLUMN status SET NOT NULL,
    ALTER COLUMN legacy_created_at SET NOT NULL;

ALTER TABLE outbox ALTER COLUMN status TYPE VARCHAR(16) USING left(status, 16);
ALTER TABLE outbox RENAME COLUMN legacy_created_at TO created_at;
ALTER TABLE outbox ALTER COLUMN created_at SET DEFAULT clock_timestamp();
ALTER TABLE outbox DROP COLUMN legacy_id;
ALTER TABLE outbox DROP COLUMN legacy_published_at;
ALTER TABLE outbox DROP COLUMN legacy_payload;
ALTER TABLE outbox DROP COLUMN saga_id;
ALTER TABLE outbox DROP COLUMN trace_id;
ALTER TABLE outbox DROP CONSTRAINT IF EXISTS outbox_events_pkey;
ALTER TABLE outbox ADD CONSTRAINT outbox_pkey PRIMARY KEY (message_id);

CREATE INDEX idx_outbox_status_created_at ON outbox (status, created_at);
CREATE INDEX idx_outbox_aggregate_created_at ON outbox (aggregate_type, aggregate_id, created_at);
