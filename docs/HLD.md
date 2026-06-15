# High-Level Design — Payments + Double-Entry Wallet Service

> Companion docs: [PLAN.md](PLAN.md) (plan & milestones) · [LLD.md](LLD.md) (schema, APIs, algorithms)

## 1. Problem & context

Merchants need to move money from a payer to a payee reliably and **exactly once**, even when
clients retry, networks fail, or the payment provider is slow. The system must keep an
auditable, always-balanced ledger and never let a wallet go negative or a payment be
double-charged.

## 2. System context

```
                              ┌─────────────────────┐
  Merchant / Client ─────────▶│   Payments API      │  Spring Boot 3 (REST)
   (Idempotency-Key header)   │   + Idempotency      │
                              └──────────┬──────────┘
                                         │
        ┌────────────────────────────────┼────────────────────────────┐
        ▼                                ▼                             ▼
  ┌────────────┐               ┌──────────────────┐          ┌──────────────┐
  │   Redis    │               │    PostgreSQL    │          │   Mock PSP   │
  │ idem fast- │               │  ledger (source  │◀──saga──▶│ authorize /  │
  │ lock, rate │               │  of truth)+outbox│          │ capture      │
  └────────────┘               └────────┬─────────┘          └──────────────┘
                                         │ outbox poll / Debezium CDC
                                         ▼
                                  ┌──────────────┐
                                  │    Kafka     │──▶ consumers: notify, analytics,
                                  └──────────────┘     ledger-projection
                                         ▲
   Mock PSP ───webhook (HMAC signed)─────┘ via  Webhook API ─▶ payment state machine
```

## 3. Core domain concepts

- **Account** — a wallet. Typed: `USER_WALLET`, `MERCHANT_PAYABLE`, `PSP_SUSPENSE`
  (money in flight to/from the provider), `FEE_INCOME`. All money movement is a transfer
  *between accounts*.
- **Ledger transaction** — an immutable journal entry grouping ≥2 **postings** whose signed
  amounts **sum to zero** (the double-entry invariant). Append-only; postings are never updated.
- **Account balance** — a *materialized* row (`balance`, `version`) updated in the **same DB
  transaction** as the postings. Postings are the source of truth; the balance row is a
  fast-read projection guarded by a non-negative `CHECK`.
- **Payment** — the business object with a state machine, realized as ledger transactions.
- **Idempotency key** — a durable record that makes non-safe `POST`s retry-safe.

## 4. Component responsibilities

| Component | Responsibility |
|-----------|----------------|
| Payments API | REST endpoints, request validation, idempotency enforcement, response replay |
| Application services | Orchestrate use cases (create payment, refund, reconcile); own transaction boundaries |
| Ledger (domain) | Enforce double-entry sum=0; post transactions; compute/maintain balances |
| PostgreSQL | Source of truth: ledger, payments, idempotency keys, outbox, webhook dedupe |
| Redis | Idempotency fast-path lock; per-merchant rate limiting |
| Mock PSP | Simulates an external provider: authorize/capture, async webhook callbacks |
| Outbox poller / CDC | Reads unpublished outbox rows → publishes to Kafka |
| Kafka + consumers | Downstream fan-out: notifications, analytics, projections |

## 5. The money-movement saga

A payment is **hold → external call → settle**, with compensation on failure:

```
1. HOLD     ledger txn: payer USER_WALLET ──▶ PSP_SUSPENSE   (debit payer; non-negative enforced)
2. CALL     mock PSP authorize/capture (network call, OUTSIDE any DB lock)
3a. SETTLE  ledger txn: PSP_SUSPENSE ──▶ payee + FEE_INCOME  → status SUCCEEDED
3b. REVERSE (PSP declined) ledger txn: PSP_SUSPENSE ──▶ payer → status FAILED
3c. PSP ok but settle write fails → leave FUNDS_LOCKED → reconciliation resolves later
```

This mirrors the classic exchange/ledger lock-settle-reverse flow; the key discipline is that
the **external call never happens inside a DB transaction holding row locks**.

## 6. Key request flows

**Create payment (`POST /v1/payments`)**
1. Idempotency check (Redis fast-path → durable DB claim).
2. In one DB transaction: insert payment (PROCESSING) + HOLD ledger txn + balance update + outbox row.
3. Commit, then call the PSP outside the transaction.
4. Second transaction: settle (SUCCEEDED) or reverse (FAILED); write outbox event.
5. Store the idempotency response for replay.

**Async settlement (`POST /v1/webhooks/psp`)**
1. Verify HMAC signature; reject on mismatch.
2. Dedupe on `psp_event_id` (idempotent).
3. Transition the payment per the event; post settle/reverse ledger txn.

**Refund (`POST /v1/payments/{id}/refunds`)**
1. Idempotency check.
2. Reverse the original postings (credit payer, debit payee/fee); status REFUNDED.

**Reconciliation (scheduled job)**
1. Find payments stuck in PROCESSING / FUNDS_LOCKED past a timeout; query PSP; resolve.
2. Assert global Σdebits = Σcredits and each balance == Σ its postings; alert on drift.

## 7. Consistency & correctness strategy

| Concern | Mechanism |
|---------|-----------|
| Exactly-once on `POST` | Idempotency key: unique constraint + cached response replay |
| No double-spend / oversell | `SELECT … FOR UPDATE` on the debited balance row + non-negative `CHECK` |
| Atomic business-change + event | **Transactional outbox**: payment + postings + balance + outbox row in ONE transaction |
| Ledger never imbalanced | App enforces sum=0; reconciliation asserts Σdebits = Σcredits |
| Concurrent state transitions | Optimistic locking (`@Version`) on `payments` with compare-and-set updates |

> **Delivery semantics:** the transactional outbox guarantees **at-least-once**, not exactly-once. Effective exactly-once comes from pairing it with **idempotent consumers** (dedupe on event id). Likewise, the materialized balance is a *cache* — postings remain the source of truth, and reconciliation asserts `balance == Σ postings` so a stored balance can never silently drift.

## 8. Technology choices & rationale

- **Spring Boot 3 / Java 21** — the dominant backend stack at the target product companies;
  strong transaction + JPA story; broadens a Go-heavy profile.
- **PostgreSQL** — ACID, row locking (`FOR UPDATE`), `CHECK` constraints, JSONB for outbox/payloads.
- **Redis** — sub-ms idempotency fast-path lock and rate limiting.
- **Kafka** — durable event fan-out; pairs with the outbox pattern for at-least-once delivery.
- **Mock PSP** — keeps the project self-contained while still exercising the saga and webhooks.
- **Java 21 virtual threads** (`spring.threads.virtual.enabled=true`) — the request path blocks on the PSP network call, the ideal virtual-thread workload; gives high throughput with plain blocking code (~3x RPS, ~70% less memory reported) while the bounded DB connection pool stays the real concurrency limiter. Avoid `synchronized` around I/O (use `ReentrantLock`) to prevent carrier-thread pinning.

## 9. Scaling & evolution (future, document don't build)

- Shard ledger by account_id; keep per-account serialization.
- Replace the outbox poller with Debezium CDC for lower latency.
- Partition Kafka topics by merchant; add consumer-group parallelism.
- Read replicas for balance/statement queries.
