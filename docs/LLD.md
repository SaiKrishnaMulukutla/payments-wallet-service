# Low-Level Design — Payments + Double-Entry Wallet Service

> Companion docs: [PLAN.md](PLAN.md) (plan & milestones) · [HLD.md](HLD.md) (architecture & flows)

## 1. Schema (PostgreSQL, via Flyway migrations)

```sql
-- Accounts ---------------------------------------------------------------
CREATE TABLE accounts (
  id          UUID PRIMARY KEY,
  owner_type  TEXT NOT NULL,          -- USER | MERCHANT | SYSTEM
  owner_id    TEXT NOT NULL,
  type        TEXT NOT NULL,          -- USER_WALLET | MERCHANT_PAYABLE | PSP_SUSPENSE | FEE_INCOME
  currency    CHAR(3) NOT NULL,
  created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (owner_type, owner_id, type, currency)
);

-- Materialized balance (fast reads + non-negative guard) -----------------
CREATE TABLE account_balances (
  account_id  UUID PRIMARY KEY REFERENCES accounts(id),
  balance     BIGINT NOT NULL DEFAULT 0,   -- minor units
  version     BIGINT NOT NULL DEFAULT 0,   -- @Version optimistic lock
  updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT non_negative CHECK (balance >= 0)   -- omit for accounts allowed to go negative (e.g. suspense)
);

-- Immutable journal ------------------------------------------------------
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
CREATE INDEX idx_entries_account ON ledger_entries(account_id, created_at);

-- Payments ---------------------------------------------------------------
CREATE TABLE payments (
  id              UUID PRIMARY KEY,
  merchant_id     TEXT NOT NULL,
  payer_account   UUID NOT NULL REFERENCES accounts(id),
  payee_account   UUID NOT NULL REFERENCES accounts(id),
  amount          BIGINT NOT NULL,
  currency        CHAR(3) NOT NULL,
  status          TEXT NOT NULL,      -- see state machine
  psp_reference   TEXT,
  version         BIGINT NOT NULL DEFAULT 0,
  created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Idempotency ------------------------------------------------------------
CREATE TABLE idempotency_keys (
  id            BIGSERIAL PRIMARY KEY,
  merchant_id   TEXT NOT NULL,
  idem_key      TEXT NOT NULL,
  request_hash  TEXT NOT NULL,        -- same key + different body → 422
  status        TEXT NOT NULL,        -- IN_PROGRESS | COMPLETED
  resource_id   UUID,                 -- the created payment
  response_code INT,
  response_body JSONB,
  created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (merchant_id, idem_key)
);

-- Transactional outbox ---------------------------------------------------
CREATE TABLE outbox (
  id             UUID PRIMARY KEY,
  aggregate_type TEXT NOT NULL,
  aggregate_id   UUID NOT NULL,
  event_type     TEXT NOT NULL,       -- payment.succeeded | payment.failed | refund.completed
  payload        JSONB NOT NULL,
  created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
  published_at   TIMESTAMPTZ
);
CREATE INDEX idx_outbox_unpublished ON outbox(created_at) WHERE published_at IS NULL;

-- Inbound webhooks (idempotent) ------------------------------------------
CREATE TABLE webhook_events (
  psp_event_id  TEXT PRIMARY KEY,     -- dedupe key
  payload       JSONB NOT NULL,
  status        TEXT NOT NULL,        -- RECEIVED | PROCESSED | FAILED
  received_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);
```

**Ledger integrity (defense in depth):** the app's `LedgerService` rejects any unbalanced transaction (Σ signed amounts ≠ 0), but enforce it at the DB too so a bug or a stray write can never corrupt the ledger — a deferred `CONSTRAINT TRIGGER` that verifies per-`transaction_id` debits = credits at `COMMIT`. Postings stay immutable (append-only); corrections are new **reversal** transactions, never `UPDATE`/`DELETE`. The `account_balances` row is a reconciled cache — postings remain the authoritative source of every balance.

## 2. Payment state machine

```
CREATED ──▶ PROCESSING ──▶ SUCCEEDED ──▶ REFUND_PENDING ──▶ REFUNDED
   │            │
   │            ├──▶ FAILED            (PSP declined → hold reversed)
   │            └──▶ FUNDS_LOCKED      (PSP ok, settle write failed → reconciliation)
```

Every transition is a guarded compare-and-set:

```sql
UPDATE payments SET status = :new, version = version + 1, updated_at = now()
WHERE id = :id AND status = :expected AND version = :version;
-- 0 rows affected ⇒ concurrent modification ⇒ abort/retry
```

## 3. API contracts

```
POST /v1/payments
  Headers: Idempotency-Key: <uuid>        (required)
  Body:    { merchantId, payerAccountId, payeeAccountId, amount, currency }
  201:     { id, status, amount, currency, createdAt }
  409:     idempotency key in progress (retry later)
  422:     same key, different request body

GET  /v1/payments/{id}                     → payment resource
POST /v1/payments/{id}/refunds             Header: Idempotency-Key → refund resource
GET  /v1/accounts/{id}/balance             → { balance, currency }
GET  /v1/accounts/{id}/ledger?cursor=...   → paginated statement (postings)
POST /v1/webhooks/psp                       Header: X-PSP-Signature (HMAC-SHA256) → 200
POST /v1/accounts                           (admin) create account
```

## 4. Idempotency algorithm (two-tier)

```
1. Redis fast-path:  SET idem:{merchant}:{key} <reqHash> NX EX 60
      acquired      → proceed
      not acquired  → fall through to the durable DB check below

2. DB durable claim:
   INSERT INTO idempotency_keys (merchant_id, idem_key, request_hash, status)
   VALUES (:m, :k, :h, 'IN_PROGRESS')
   ON CONFLICT (merchant_id, idem_key) DO NOTHING;

      inserted (1 row) → you own the request; do the work; UPDATE row to COMPLETED + store response
      conflict (0 rows) → read the existing row:
          status = COMPLETED        → replay stored response_code/response_body
          status = IN_PROGRESS      → 409 retry-later
          request_hash mismatch     → 422 (key reused with a different body)
```

Redis is the fast path; Postgres is the durable arbiter (survives a Redis flush).

**Response caching (Stripe behaviour):** persist the first request's status code + body for **both success and failure**, so a retry replays even a `5xx` identically rather than re-running side effects. Keys are client-generated **UUIDv4**, ≤255 chars, scoped by `(merchant_id, idem_key)`, and pruned after **24h** (a reused key after pruning starts a fresh request). Follow the IETF `Idempotency-Key` header draft for the wire contract.

## 5. The critical transaction boundary

`PaymentService.create()` uses **two transactions**, never one spanning the network call:

**Tx 1 (`@Transactional`):**
1. Claim idempotency key.
2. Insert `payment` (status PROCESSING).
3. `SELECT … FOR UPDATE` payer `account_balances` row.
4. Insert HOLD `ledger_transaction` + two `ledger_entries` (payer DEBIT, suspense CREDIT).
5. Update `account_balances` (fails on the non-negative `CHECK` if insufficient funds).
6. Insert `outbox` row (`payment.processing`).
7. Commit.

**Network:** call the Mock PSP **outside** any transaction.

**Tx 2 (`@Transactional`):**
- On success: SETTLE ledger txn (suspense → payee + fee), CAS payment → SUCCEEDED, outbox `payment.succeeded`.
- On decline: REVERSE the hold, CAS payment → FAILED, outbox `payment.failed`.
- On settle-write failure after PSP success: CAS payment → FUNDS_LOCKED (reconciliation owns it).

## 6. Concurrency & locking

- **Debit path:** pessimistic `SELECT … FOR UPDATE` on the payer balance row — serializes
  concurrent debits per account, which is exactly the ledger requirement. Simpler and more
  predictable than optimistic retries on a hot wallet.
- **Payment status:** optimistic `@Version` + compare-and-set (low contention; transitions are rare).
- **Isolation level:** `READ COMMITTED` (Postgres default) is sufficient given the explicit row locks.
- **Why not hold the lock across the PSP call:** a network call can take seconds; holding a row
  lock that long would serialize the whole account and exhaust the connection pool.
- **Virtual threads (Java 21):** enable `spring.threads.virtual.enabled=true` — the PSP call is
  blocking I/O, the ideal virtual-thread workload. Two rules keep it safe: (1) avoid `synchronized`
  around I/O — use `ReentrantLock` — or the virtual thread *pins* its carrier and you lose the
  benefit; (2) the bounded **HikariCP pool**, not the (cheap, unbounded) virtual-thread count, is
  the true concurrency limit, so set `leak-detection-threshold` and size the pool deliberately.
  Detect pinning in staging with `-Djdk.tracePinnedThreads=short`.

## 7. Transactional outbox + Kafka

- Business change and the `outbox` insert commit **atomically** in the same transaction — this
  is what eliminates the dual-write problem (DB says X, but the event was lost / or vice-versa).
- A scheduled **OutboxPoller** (`@Scheduled`) selects `WHERE published_at IS NULL`, publishes to
  Kafka, then stamps `published_at`. (Upgrade path: Debezium CDC on the `outbox` table.)
- Consumers are **idempotent** (dedupe on event id) since delivery is at-least-once.

## 8. Webhook signature verification

```
received  = header X-PSP-Signature
expected  = HMAC-SHA256(secret, rawRequestBody)          // raw bytes, before JSON parse
verify    = MessageDigest.isEqual(expected, received)    // constant-time compare
```
Reject with 401 on mismatch. Read the raw body **once** and reset it so the controller can
re-read for parsing. Dedupe on `psp_event_id` before applying any state change.

## 9. Package layout (Spring Boot, hexagonal-ish)

```
com.skm.payments
├── api/            controllers, DTOs, GlobalExceptionHandler, IdempotencyFilter
├── application/    PaymentService, RefundService, ReconciliationService (orchestration, @Transactional)
├── domain/         Account, Payment, LedgerTransaction, LedgerService (sum=0 invariant), state machine
├── infrastructure/
│   ├── persistence/  JPA entities + repositories, Flyway migrations
│   ├── psp/          MockPspClient
│   ├── messaging/    OutboxPoller, KafkaPublisher, event consumers
│   └── redis/        IdempotencyLock, RateLimiter
└── config/         beans, properties, security
```

## 10. Error handling & failure modes

| Failure | Behaviour |
|---------|-----------|
| Duplicate request (same key) | Replay cached response; no new side effects |
| Same key, different body | 422 Unprocessable Entity |
| Insufficient funds | Non-negative `CHECK` violation → 422; payment FAILED, hold rolled back |
| PSP timeout / 5xx | Reverse hold → FAILED, or retry within policy; never silently drop |
| PSP success but settle write fails | FUNDS_LOCKED → reconciliation job resolves |
| Redis down | Fall back to DB-only idempotency (correctness preserved, latency up) |
| Duplicate webhook | Deduped on `psp_event_id`; second delivery is a no-op |

## 11. Testing specifics

- **Ledger invariant test:** any posted transaction has Σ(signed amounts) = 0; balance == Σ postings.
- **Oversell test:** 100 threads debit one wallet with balance for ~50 → exactly the affordable
  number succeed, balance never < 0.
- **Idempotency test:** 50 threads `POST` the same Idempotency-Key → one payment row, one ledger
  transaction, all responses identical.
- **Webhook test:** tampered body → 401; replayed `psp_event_id` → single state change.
- Use **Testcontainers** for Postgres / Redis / Kafka in integration tests.
