# Payments + Double-Entry Wallet Service — Project Plan

A production-grade payments backend with a double-entry ledger as the source of truth,
idempotent payment APIs, a hold→settle saga against a mock payment provider, async
settlement via signed webhooks, refunds/reversals, a reconciliation job, a transactional
outbox to Kafka, and full observability.

**Stack:** Java 21 (virtual threads) · Spring Boot 3.4 · PostgreSQL · Redis · Kafka · Docker · Micrometer/Prometheus/Grafana

> Companion docs: [HLD.md](HLD.md) (high-level design) · [LLD.md](LLD.md) (low-level design)

---

## 0. Best-practice decisions (researched, June 2026)

Validated against how Stripe, Modern Treasury, TigerBeetle, and the current Spring community build these systems:

- **Enable Java 21 virtual threads** (`spring.threads.virtual.enabled=true`). The hot path is dominated by a *blocking* PSP network call — the ideal virtual-thread workload (reported ~3x throughput, ~70% less memory on payment endpoints vs platform-thread pools). Caveats baked into the design: avoid `synchronized` around I/O (use `ReentrantLock`) to prevent carrier-thread *pinning*, set HikariCP `leak-detection-threshold`, and let the bounded DB pool be the real concurrency limiter.
- **Idempotency caches the first response whether it succeeded OR failed** (status + body) so retries replay even a 5xx identically — Stripe's exact behaviour. Keys are client-generated UUIDv4, ≤255 chars, pruned after 24h; follow the IETF `Idempotency-Key` header draft.
- **Balances are derived from immutable postings; the materialized balance row is a continuously-reconciled cache, not the source of truth** (Modern Treasury / TigerBeetle principle — a stored balance treated as authoritative will drift). Reconciliation asserts `balance == Σ postings` and global `Σdebits == Σcredits`.
- **The outbox gives at-least-once, not exactly-once.** Effective exactly-once = transactional outbox **+ idempotent consumers** (dedupe on event id). Start with a polling relay (simpler to build/debug/operate); migrate to Debezium CDC only if latency or DB load demands — the outbox table schema is identical either way.

Sources are listed at the bottom of this file.

## 1. Goals

- Demonstrate **exactly-once** payment processing via idempotency keys.
- A correct **double-entry ledger** with a non-negative-balance invariant under concurrency.
- A **saga** (lock → external call → settle) with compensating reversals.
- **Idempotent webhook** ingestion with HMAC signature verification.
- The **transactional outbox** pattern (atomic business-change + event publish).
- **Real measured numbers** (load test) and dashboards — not invented metrics.

## 2. Non-Goals (deliberately cut — knowing the boundary is a signal)

- Real bank/PSP rails, card networks, KYC/AML.
- Multi-currency FX conversion (one currency per account).
- Horizontal sharding of Postgres (single primary; document how you'd shard later).
- A UI. This is a backend/API project.

## 3. Money representation (decide once)

Store money as **`BIGINT` minor units** (paise/cents) + a `currency` column. Exact integer
arithmetic, no float/rounding bugs. (Contrast for interviews: crypto ledgers use `decimal(30,18)`
for 18-dp assets; fiat payments use minor-unit integers — be ready to explain the difference.)

## 4. Build milestones (~1 focused session each)

| M | Goal | Done when… |
|---|------|------------|
| **M0** | Skeleton | Docker Compose (Postgres + Redis + Kafka) up, Spring Boot boots, Flyway migrates, `/actuator/health` green |
| **M1** | Ledger core | Transfer primitive enforces double-entry sum=0 + non-negative; **concurrency test**: 100 parallel debits never oversell |
| **M2** | Payments + idempotency | Create payment, hold→settle saga vs in-process mock PSP; **test**: 50 parallel identical requests → exactly 1 payment |
| **M3** | Outbox + Kafka | Outbox poller (or Debezium CDC) publishes `payment.*` events; a consumer logs them |
| **M4** | Webhooks | HMAC-verified inbound webhook, idempotent on `psp_event_id`, async capture path |
| **M5** | Refunds + reconciliation | Refund reverses entries; reconciliation job resolves `FUNDS_LOCKED` and asserts Σdebits = Σcredits |
| **M6** | Observability + load test | Micrometer → Prometheus → Grafana; k6 load test → real p99 / RPS numbers; README + architecture diagram |

## 5. Testing strategy

- **Unit:** ledger invariants (sum=0, non-negative), state-machine transitions.
- **Integration:** Testcontainers for Postgres / Redis / Kafka.
- **Concurrency (the proof):**
  - 100 parallel debits on one wallet → balance never negative, no oversell.
  - 50 parallel identical `POST /payments` (same Idempotency-Key) → exactly one payment, one ledger transaction.
- **Idempotency replay:** repeat a completed request → identical cached response, no new side effects.

## 6. Observability (fixes the "no metrics" resume gap)

Instrument with Micrometer → Prometheus → Grafana:

- payment throughput & p99 latency
- idempotency-hit rate
- saga compensation (reversal) count
- **`ledger_imbalance` gauge that must always read 0** — alert if it ever isn't

Then run **k6** to capture a real "sustained _X_ payments/sec at p99 _Y_ ms" line for the resume.

## 7. Definition of done (resume-grade, not toy)

- [ ] README with an architecture diagram + a "Design decisions & trade-offs" section
- [ ] Runs end-to-end via `docker compose up`
- [ ] The two concurrency tests pass and are documented
- [ ] Real load-test numbers in the README
- [ ] Grafana dashboard screenshot committed

## 8. Interview talking points this unlocks

Exactly-once via idempotency keys · the dual-write problem and why the transactional outbox
solves it · double-entry + non-negative under concurrency · optimistic vs `SELECT … FOR UPDATE`
pessimistic locking (and why you never hold a DB lock across the PSP network call) · saga /
compensation · idempotent webhook processing with signature verification.

## References (researched June 2026)

- Stripe — [Designing robust and predictable APIs with idempotency](https://stripe.com/blog/idempotency) · [Idempotent requests (API ref)](https://docs.stripe.com/api/idempotent_requests)
- Modern Treasury — [Enforcing immutability in your double-entry ledger](https://www.moderntreasury.com/journal/enforcing-immutability-in-your-double-entry-ledger) · [How to scale a ledger, Part V](https://www.moderntreasury.com/journal/how-to-scale-a-ledger-part-v)
- TigerBeetle — [Debit/Credit: the schema for OLTP](https://docs.tigerbeetle.com/concepts/debit-credit/)
- Transactional outbox — [Reliable event publishing](https://james-carr.org/posts/2026-01-15-transactional-outbox-pattern/) · [Outbox vs CDC trade-offs](https://singhajit.com/transactional-outbox-pattern/)
- Virtual threads — [Spring Boot 3.2+ / Java 21 high-throughput guide](https://www.springboot-123.com/en/blog/spring-boot-virtual-threads-java21-guide/) · [Virtual threads in Spring Boot 3.4+](https://ankurm.com/leveraging-virtual-threads-in-spring-boot-3-4-building-high-throughput-services/)
