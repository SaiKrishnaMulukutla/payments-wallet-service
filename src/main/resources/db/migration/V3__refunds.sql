-- ============================================================================
-- V3: refunds. A captured payment can be refunded (full or partial) by reversing
-- funds payee -> payer. refunded_amount on payments tracks the running total so
-- over-refunds are rejected.
-- ============================================================================
ALTER TABLE payments ADD COLUMN refunded_amount BIGINT NOT NULL DEFAULT 0;

CREATE TABLE refunds (
  id          UUID PRIMARY KEY,
  payment_id  UUID NOT NULL REFERENCES payments(id),
  amount      BIGINT NOT NULL CHECK (amount > 0),
  status      TEXT NOT NULL,
  created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_refunds_payment ON refunds (payment_id);
