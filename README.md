# Payments + Double-Entry Wallet Service

A production-grade payments backend: a double-entry ledger as the source of truth,
idempotent payment APIs, a hold→settle saga against a mock payment provider, async
settlement via signed webhooks, refunds/reversals, reconciliation, a transactional
outbox to Kafka, and full observability.

**Stack:** Java 21 (virtual threads) · Spring Boot 3.4 · PostgreSQL · Redis · Kafka · Docker

Design docs: [PLAN.md](PLAN.md) · [HLD.md](HLD.md) · [LLD.md](LLD.md)

---

## Run it (no JDK needed — everything builds in Docker)

```bash
docker compose up --build
```

This builds the app inside a `gradle:jdk21` image and starts Postgres, Redis, Kafka,
and the service. First build downloads dependencies and may take a few minutes.

**Verify M0 is live:**

```bash
curl localhost:8080/actuator/health      # -> {"status":"UP", ...}
```

Flyway applies `V1__init.sql` on startup (check the logs for `Migrating schema ... to version "1 - init"`).

Stop everything:

```bash
docker compose down            # add -v to also drop the Postgres volume
```

---

## Project layout

```
src/main/java/com/skm/payments/
├── PaymentsApplication.java        # entry point
├── domain/                         # Account, AccountBalance, Ledger{Transaction,Entry}, Direction
│   └── LedgerService.java          # post() + the double-entry sum-zero invariant
├── repository/                     # Spring Data JPA repos (incl. FOR UPDATE balance lookup)
├── api/                            # REST controllers (M2+)
└── config/                         # beans/config (M2+)
src/main/resources/
├── application.yml                 # datasource, redis, kafka, flyway, virtual threads, actuator
└── db/migration/V1__init.sql       # schema + deferred double-entry constraint trigger + system accounts
```

## Status — Milestone M0 (skeleton)

- [x] Docker Compose: Postgres + Redis + Kafka + app
- [x] Spring Boot 3.4 / Java 21 with virtual threads enabled
- [x] Flyway schema (ledger, payments, idempotency, outbox, webhooks) + double-entry trigger
- [x] Core JPA entities + repositories
- [x] `LedgerService` with the sum-zero invariant (balance maintenance is M1)
- [x] Testcontainers context-load test

Next: **M1** — implement balance maintenance under `SELECT ... FOR UPDATE` with the
non-negative guard, and the 100-parallel-debit oversell test. See [PLAN.md](PLAN.md).

> Note: the base package is `com.skm.payments` (placeholder) — rename to your own group if you like.
