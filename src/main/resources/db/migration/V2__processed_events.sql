-- ============================================================================
-- V2: consumer-side dedup store. The transactional outbox delivers at-least-once;
-- consumers record each handled event id here and skip duplicates, making
-- processing effectively exactly-once. (The `outbox` table itself is in V1.)
-- ============================================================================
CREATE TABLE processed_events (
  event_id     UUID PRIMARY KEY,
  processed_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
