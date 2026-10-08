# requirement.md — Hardening the Distributed Order Management System

## 0. How to read this document

This project is a Spring Boot + Kafka + PostgreSQL saga-orchestration system (Order, Inventory, Payment, Saga Orchestrator, shared common DTO module).

The goal is **not** to add flashy features. The goal is to make the system **correct under failure** and to make that correctness **provable with tests** and **honestly documented**.

**Important rules for whoever implements this (human or AI):**

1. **The code is the source of truth.** Some items below may already be partly or fully implemented. Verify in the code before assuming anything is missing. Mark each requirement as `DONE`, `PARTIAL`, or `MISSING` in the design.
2. Do not claim a guarantee (exactly-once, no data loss, etc.) unless a test proves it.
3. Prefer simple, explainable solutions over clever ones. The author must be able to defend every decision in an interview.
4. Every requirement has acceptance criteria. A requirement is not done until its criteria are met and tested.
5. Keep changes incremental. Each phase should leave the system runnable.

---

## 1. Current state (from author's own summary)

Implemented: order creation, saga success flow, two compensation flows (inventory failure, payment failure), Kafka command/event topics, retry + DLQ, traceId propagation via MDC, `@Version` optimistic locking, BigDecimal money handling, idempotency checks in Inventory, shared common DTO module.

Order statuses: `CREATED, INVENTORY_RESERVED, PAYMENT_SUCCESS_PENDING, PAYMENT_FAILED_PENDING, FAILED, CANCELLED, COMPLETED`.

Known suspected gaps (to be verified against the code): no transactional outbox, orchestrator likely stateless, idempotency maybe only in Inventory, no timeouts, no automated failure tests, compensation is fire-and-forget, partition key/ordering unclear, README overclaims.

---

## 2. Phases and requirements

Priority order: Phase 0 → 1 → 2 → 3 → 4 → 5. Phase 6 is optional.

---

### Phase 0 — Baseline and run-ability

**R0.1 Audit the as-is system.**
Document what actually exists: topics, partition keys, producer/consumer configs, retry config, DB schemas, where idempotency lives, how the orchestrator decides next steps, and how the order status transitions happen.
*Acceptance:* an "as-is" section in `design.md` with file/class references for every claim.

**R0.2 One-command local environment.**
Docker Compose for Kafka, PostgreSQL (separate database or schema per service), and all four services.
*Acceptance:* `docker compose up` brings the stack up from a clean clone; README has exact run steps.

**R0.3 Public REST API for orders.**
`POST /orders`, `GET /orders/{id}`, with OpenAPI docs. `GET` must expose the current order status and, ideally, saga status.
*Acceptance:* a demo script (curl or `.http` file) runs success, inventory-failure, and payment-failure flows end to end.

---

### Phase 1 — Correctness core (highest priority)

**R1 Transactional outbox in every service that publishes to Kafka.**
State change and event/command must be written in the same DB transaction (`outbox` table). A separate publisher (poller, or Debezium CDC if justified) publishes and marks rows as sent.
- Handle publisher crash/restart without losing messages (at-least-once is acceptable).
- Support multiple instances of the publisher safely (e.g. `FOR UPDATE SKIP LOCKED`).
- Define cleanup/retention of sent outbox rows.
*Acceptance:* a test that kills the process (or fails the publish) after DB commit and before Kafka publish proves the message is still eventually delivered.

**R2 Persistent saga state in the orchestrator.**
A `saga_instance` table (at minimum: `saga_id`, `order_id`, `current_step`, `status`, `started_at`, `updated_at`, `deadline_at`, `version`) plus a `saga_step_log` / transition history table for audit.
- Every event handled by the orchestrator updates saga state and writes outgoing commands (via outbox) in one transaction.
- Orchestrator can restart at any point and resume correctly.
- Orchestrator handlers must be idempotent and must ignore events that are invalid for the current state (duplicates, late, out-of-order), while logging and counting them.
*Acceptance:* tests for duplicate events, out-of-order events, and orchestrator restart mid-saga.

**R3 Idempotent consumers in every service (not just Inventory).**
Payment must never double-charge for the same saga/order. Choose and document one approach (processed-messages table with unique key, or natural idempotency key with unique constraint). The idempotency check and the business write must be in the **same transaction**.
- Idempotency key must be deterministic (e.g. `sagaId + commandType`), not a random per-delivery id.
- Duplicate delivery must produce the same observable outcome (re-publish the original result event if needed).
*Acceptance:* tests that deliver the same command 2+ times (including concurrently) and assert exactly one business effect.

**R4 Kafka correctness configuration.**
- Message key = `orderId` (or `sagaId`) for every topic, so per-order ordering is preserved. Document partition count and why.
- Producer: `acks=all`, `enable.idempotence=true`, sensible retries/timeouts.
- Consumer: manual offset commit **after** the DB transaction commits (or equivalent safe pattern); explicit `isolation`/`auto.offset.reset` choices.
- Document consumer concurrency and what happens during a rebalance mid-processing.
*Acceptance:* config documented in design; test or documented reasoning for ordering and no-loss behaviour.

**R5 Correct compensation semantics.**
- On payment failure: orchestrator sends `inventory.release.cmd`, **waits for** `inventory.released` (new event), and only then cancels the order. Release failure must be retried and eventually escalated (alert/manual-intervention state), not silently ignored.
- Add **payment refund compensation** (`payment.refund.cmd` / `payment.refunded`) for the case where payment succeeded but a later step (e.g. order confirmation) fails.
- Define which steps are compensable and which are the point of no return.
- Compensation commands must themselves be idempotent.
*Acceptance:* tests for: payment fails, release succeeds; payment fails, release fails then retries; payment succeeds, confirm fails, refund + release happen.

**R6 Explicit state machines.**
- Define allowed transitions for Order status and Saga status in one place (transition table or state pattern). Illegal transitions are rejected and logged.
- Clearly define `FAILED` vs `CANCELLED`, and justify or remove `PAYMENT_SUCCESS_PENDING` / `PAYMENT_FAILED_PENDING`.
- Provide a state diagram in the docs.
*Acceptance:* unit tests covering every allowed and several illegal transitions.

**R7 Timeouts and stuck-saga detection.**
- Each saga step has a deadline. A watchdog (scheduled job, safe with multiple instances) finds sagas past their deadline and either retries the step, triggers compensation, or marks them `NEEDS_ATTENTION`.
- Handle "Payment never replies" and "Inventory never replies" explicitly.
- Late replies arriving after a timeout/compensation must be handled safely (ignored or compensated), never corrupting state.
*Acceptance:* tests simulating a non-responding service and a late reply.

**R8 Retry and DLQ strategy.**
- Distinguish **transient** errors (retry with backoff) from **permanent/poison** messages (straight to DLQ).
- Use non-blocking retries (retry topics with backoff) or clearly justify blocking retries and their cost.
- DLQ messages keep original headers plus failure reason and stack info.
- Provide a way to inspect and **replay** a DLQ message (admin endpoint or CLI/script), with idempotency making replay safe.
- Add a DLQ metric/alert, not just a log line.
*Acceptance:* tests for transient-then-success, poison-to-DLQ, and DLQ replay.

---

### Phase 2 — Automated testing (can run in parallel with Phase 1; each Phase 1 item ships with its tests)

**R9 Unit tests** for the state machines, orchestrator decision logic, idempotency logic, and money calculations.

**R10 Integration tests with Testcontainers** (Kafka + PostgreSQL) covering at least:
1. Happy path.
2. Inventory failure → order cancelled.
3. Payment failure → inventory released → order cancelled.
4. Duplicate command / duplicate event delivery.
5. Out-of-order / late events.
6. Crash between DB commit and Kafka publish (outbox recovery).
7. Orchestrator restart mid-saga.
8. Timeout / non-responding service.
9. Concurrent orders competing for the last unit of stock.
10. Poison message → DLQ → replay.

**R11 Failure-injection ("chaos-lite") scenario.**
A scripted test or runbook that stops a service mid-saga and shows the system converges to a consistent terminal state after restart.
*Acceptance for Phase 2:* `mvn verify` runs everything green from a clean checkout; CI workflow (GitHub Actions) runs it on every push.

---

### Phase 3 — Observability

**R12 Real distributed tracing.** Replace/augment MDC-only correlation with OpenTelemetry (spans across HTTP and Kafka, trace context in message headers). Keep `sagaId` / `orderNumber` as span attributes and log fields. Include Jaeger or Tempo in Docker Compose.

**R13 Metrics.** Micrometer + Prometheus: sagas started/completed/compensated/stuck, step latency, outbox backlog size, consumer lag, retry count, DLQ count, duplicate-event count. Grafana dashboard JSON committed to the repo.

**R14 Structured logging** with consistent fields (`traceId`, `sagaId`, `orderId`, `step`) and an audit trail of every saga/order state transition.

*Acceptance:* one screenshot or documented walkthrough showing a single order traced across all services plus a dashboard showing a failure scenario.

---

### Phase 4 — Data correctness and API robustness

**R15 Client idempotency key on order creation.** `Idempotency-Key` header on `POST /orders`; same key returns the original response and never creates a second order. Store the request fingerprint; reject the same key with a different payload.

**R16 Inventory reservation under contention.**
Compare and choose between: atomic conditional update (`UPDATE ... SET available = available - :q WHERE sku = :s AND available >= :q`), pessimistic lock, and optimistic lock with bounded retry. Document the choice with reasoning. If `@Version` stays, define the retry policy and what happens when retries are exhausted.
*Acceptance:* concurrency test (many threads, limited stock) proving no oversell and no lost reservation.

**R17 Reservation expiry (TTL).** Reserved stock that is not confirmed within a configured time is released automatically (tied into the saga timeout/watchdog logic).

**R18 Basic API hygiene.** Input validation, consistent error responses, and no sensitive data in logs. (Full authN/authZ is out of scope unless time allows; list it under Known Limitations.)

---

### Phase 5 — Documentation honesty

**R19 Rewrite the README.**
- Remove unprovable claims (see section 3 below).
- Add: quick start, architecture diagram, sequence diagrams for each flow, state diagrams, topic/partition table, delivery guarantees actually provided (stated precisely), how to run tests, and screenshots of tracing/metrics.
- Add a **"Known limitations and trade-offs"** section.

**R20 Architecture Decision Records (`docs/adr/`).** At minimum: orchestration vs choreography; outbox poller vs CDC; idempotency approach; retry/DLQ approach; inventory concurrency approach; shared DTO module vs versioned contracts.

**R21 Failure-scenario matrix.** A table in the docs: *scenario → what happens → how it is tested*. This doubles as interview preparation.

---

### Phase 6 — Optional advanced (only after Phases 1–5)

- **O1** Schema versioning: Avro or Protobuf with a schema registry, or at least versioned event payloads plus consumer-driven contract tests; stop sharing a mutable DTO jar across services.
- **O2** Benchmark write-up: throughput, p99 latency, retry rate for inventory reservation (atomic update vs optimistic lock) with real numbers.
- **O3** Payment realism: an "unknown outcome" state, reconciliation job, and duplicate/late provider callback handling.
- **O4** Compare against a durable-workflow engine (Temporal or similar) in an ADR: what it gives you that the hand-rolled orchestrator does not.
- **O5** Multi-instance run: run 2+ instances of each service and prove partition-aware, duplicate-safe behaviour.

---

## 3. README claims to remove or reword

| Current claim | Required change |
|---|---|
| "guarantees eventual consistency" | State it as a goal; describe the exact mechanisms (outbox, idempotency, timeouts) only after they exist and are tested. |
| "Cross-service consistency without distributed transactions" | Say sagas provide eventual consistency with visible intermediate states. |
| "Production-style / production-inspired" | Use "learning project modelled on production patterns" unless the criteria above are met. |
| "Distributed tracing" | Use only after R12 (OpenTelemetry) is done. Until then call it "correlation-ID propagation". |
| "Idempotent consumers" | Only claim per service where it is implemented and tested. |
| "Optimistic locking prevents conflicts" | Say it **detects** conflicts; describe the retry/atomic-update strategy. |
| "Fault tolerant design" | Describe concrete failure modes handled and link to tests. |
| "Used at Amazon, Uber, Swiggy, Flipkart…" | Remove. |
| "This is not a CRUD application" | Remove. |
| "Distributed Transactions" in the concepts list | Replace with "Saga pattern (alternative to distributed transactions)". |

---

## 4. Non-goals

- A UI.
- Rewriting everything in a new framework.
- Claiming exactly-once delivery. The system targets **at-least-once delivery plus idempotent processing**.
- Full security (authN/authZ) — listed as a known limitation.

---

## 5. Definition of done (whole project)

1. `docker compose up` + demo script reproduces success, inventory-failure, payment-failure, and timeout flows.
2. `mvn verify` passes all unit and Testcontainers integration tests; CI is green.
3. Killing any single service mid-saga and restarting it leads to a consistent terminal state, proven by a test.
4. No double charge and no oversell under duplicate delivery and concurrency, proven by tests.
5. README makes only claims backed by tests, and includes Known Limitations, diagrams, and the failure-scenario matrix.
6. The author can explain every ADR and every failure-scenario row without notes.

## Addendum (from audit)
- R22: Fix the duplicate sendInventoryReserved call in InventoryServiceImpl.processReserve, with a regression test.
- R23: Single writer of Order status. Decide whether order-service reacts to domain events or to orchestrator commands, not both. Document the decision.
- R24: Add a parent/aggregator pom so one `mvn verify` builds and tests every module (needed for CI).
- R25: sagaId is a String UUID everywhere; fix the OrderSaga entity type.
- R26: Fix or confirm the CANCELLED vs FAILED outcome so every compensated order reaches one well-defined terminal state.
