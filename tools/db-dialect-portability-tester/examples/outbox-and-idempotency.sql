-- The outbox and idempotency schema from bpmn-provisioning-patterns, plus the statements the
-- application actually issues against it. Replayed on both engines, this is what a project would
-- point the tool at: the migration, then the real queries, in the order they run.
--
-- Statements 5 and 7 diverge on purpose. They are here because both mistakes were made in this
-- ecosystem and neither was caught by a test suite running on H2 alone.

-- name: migration - outbox table
CREATE TABLE portability_outbox (
    id VARCHAR(36) PRIMARY KEY,
    aggregate_id VARCHAR(64) NOT NULL,
    event_type VARCHAR(128) NOT NULL,
    payload VARCHAR(4000) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    published_at TIMESTAMP NULL,
    attempts INTEGER NOT NULL DEFAULT 0,
    last_error VARCHAR(2000) NULL
);

-- name: migration - processed events table
CREATE TABLE processed_events (
    event_id VARCHAR(64) NOT NULL,
    topic VARCHAR(128) NOT NULL,
    aggregate_id VARCHAR(64) NOT NULL,
    processed_at TIMESTAMP NOT NULL,
    CONSTRAINT pk_processed_events PRIMARY KEY (event_id)
);

-- name: seed one pending row
INSERT INTO portability_outbox (id, aggregate_id, event_type, payload, created_at)
VALUES ('11111111-1111-1111-1111-111111111111', 'REQ-1', 'PortRequested', '{}', TIMESTAMP '2026-01-01 10:00:00');

-- name: relay - claim the pending batch
SELECT id, aggregate_id, event_type, payload
FROM portability_outbox
WHERE published_at IS NULL
ORDER BY created_at;

-- name: relay - claim the batch, with the table name quoted
SELECT id FROM "portability_outbox" WHERE published_at IS NULL;

-- name: relay - mark one row published
UPDATE portability_outbox SET published_at = TIMESTAMP '2026-01-01 10:00:01'
WHERE id = '11111111-1111-1111-1111-111111111111';

-- name: build a diagnostic label by concatenation
SELECT 'attempt ' || attempts FROM portability_outbox;

-- name: idempotency - claim an event id
INSERT INTO processed_events (event_id, topic, aggregate_id, processed_at)
VALUES ('evt-1', 'donor-response-events', 'REQ-1', TIMESTAMP '2026-01-01 10:00:02');

-- name: idempotency - the redelivery must be refused by the primary key
INSERT INTO processed_events (event_id, topic, aggregate_id, processed_at)
VALUES ('evt-1', 'donor-response-events', 'REQ-1', TIMESTAMP '2026-01-01 10:00:03');

-- name: retention - purge processed events older than the cutoff
DELETE FROM processed_events WHERE processed_at < TIMESTAMP '2025-01-01 00:00:00';
