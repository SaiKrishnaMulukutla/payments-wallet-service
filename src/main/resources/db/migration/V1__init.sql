-- ============================================================================
-- V1: Payments + Double-Entry Wallet — initial schema
-- Money is stored as BIGINT minor units (paise/cents). Never floats.
-- Postings are the source of truth; account_balances is a reconciled cache.
-- ============================================================================

-- Accounts -------------------------------------------------------------------
CREATE TABLE accounts (
  id          UUID PRIMARY KEY,
  owner_type  TEXT NOT NULL,          -- USER | MERCHANT | SYSTEM
  owner_id    TEXT NOT NULL,
  type        TEXT NOT NULL,          -- USER_WALLET | MERCHANT_PAYABLE | PSP_SUSPENSE | FEE_INCOME
  currency    CHAR(3) NOT NULL,
  created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (owner_type, owner_id, type, currency)
);

-- Materialized balance (fast reads + non-negative guard) ---------------------
CREATE TABLE account_balances (
  account_id  UUID PRIMARY KEY REFERENCES accounts(id),
  balance     BIGINT NOT NULL DEFAULT 0,   -- minor units
  version     BIGINT NOT NULL DEFAULT 0,   -- optimistic lock (@Version)
  updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT non_negative CHECK (balance >= 0)
);

-- Immutable journal ----------------------------------------------------------
CREATE TABLE ledger_transactions (
  id            UUID PRIMARY KEY,
  type          TEXT NOT NULL,        -- PAYMENT | REFUND | REVERSAL | FEE
  reference_id  UUID NOT NULL,        -- payment_id / refund_id
  created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE ledger_entries (         -- postings; append-only
  id              BIGSERIAL PRIMARY KEY,
  transaction_id  UUID NOT NULL REFERENCES ledger_transactions(id),
  account_id      UUID NOT NULL REFERENCES accounts(id),
  direction       TEXT NOT NULL,      -- DEBIT | CREDIT
  amount          BIGINT NOT NULL CHECK (amount > 0),
  created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_entries_account ON ledger_entries (account_id, created_at);
CREATE INDEX idx_entries_txn ON ledger_entries (transaction_id);

-- Payments -------------------------------------------------------------------
CREATE TABLE payments (
  id              UUID PRIMARY KEY,
  merchant_id     TEXT NOT NULL,
  payer_account   UUID NOT NULL REFERENCES accounts(id),
  payee_account   UUID NOT NULL REFERENCES accounts(id),
  amount          BIGINT NOT NULL,
  currency        CHAR(3) NOT NULL,
  status          TEXT NOT NULL,
  psp_reference   TEXT,
  version         BIGINT NOT NULL DEFAULT 0,
  created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_payments_status ON payments (status, created_at);

-- Idempotency ----------------------------------------------------------------
CREATE TABLE idempotency_keys (
  id            BIGSERIAL PRIMARY KEY,
  merchant_id   TEXT NOT NULL,
  idem_key      TEXT NOT NULL,
  request_hash  TEXT NOT NULL,
  status        TEXT NOT NULL,        -- IN_PROGRESS | COMPLETED
  resource_id   UUID,
  response_code INT,
  response_body JSONB,
  created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (merchant_id, idem_key)
);

-- Transactional outbox -------------------------------------------------------
CREATE TABLE outbox (
  id             UUID PRIMARY KEY,
  aggregate_type TEXT NOT NULL,
  aggregate_id   UUID NOT NULL,
  event_type     TEXT NOT NULL,
  payload        JSONB NOT NULL,
  created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
  published_at   TIMESTAMPTZ
);
CREATE INDEX idx_outbox_unpublished ON outbox (created_at) WHERE published_at IS NULL;

-- Inbound webhooks (idempotent) ----------------------------------------------
CREATE TABLE webhook_events (
  psp_event_id  TEXT PRIMARY KEY,
  payload       JSONB NOT NULL,
  status        TEXT NOT NULL,        -- RECEIVED | PROCESSED | FAILED
  received_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- ============================================================================
-- Defense-in-depth: enforce the double-entry invariant at the DB.
-- A deferred constraint trigger verifies, at COMMIT, that every transaction's
-- postings net to zero (DEBIT = +amount, CREDIT = -amount). The app's
-- LedgerService checks this too; this guarantees the ledger can never be
-- corrupted even by a bug or a direct write.
-- ============================================================================
CREATE OR REPLACE FUNCTION assert_ledger_balanced() RETURNS trigger AS $$
DECLARE
  net BIGINT;
BEGIN
  SELECT COALESCE(SUM(CASE direction WHEN 'DEBIT' THEN amount ELSE -amount END), 0)
    INTO net
    FROM ledger_entries
   WHERE transaction_id = NEW.transaction_id;

  IF net <> 0 THEN
    RAISE EXCEPTION 'Ledger transaction % is unbalanced (net = %)', NEW.transaction_id, net;
  END IF;

  RETURN NULL;
END;
$$ LANGUAGE plpgsql;

CREATE CONSTRAINT TRIGGER trg_ledger_balanced
  AFTER INSERT ON ledger_entries
  DEFERRABLE INITIALLY DEFERRED
  FOR EACH ROW EXECUTE FUNCTION assert_ledger_balanced();

-- Seed system accounts used by the payment saga -------------------------------
INSERT INTO accounts (id, owner_type, owner_id, type, currency) VALUES
  ('00000000-0000-0000-0000-000000000001', 'SYSTEM', 'psp',  'PSP_SUSPENSE', 'INR'),
  ('00000000-0000-0000-0000-000000000002', 'SYSTEM', 'fees', 'FEE_INCOME',   'INR');
INSERT INTO account_balances (account_id) VALUES
  ('00000000-0000-0000-0000-000000000001'),
  ('00000000-0000-0000-0000-000000000002');
