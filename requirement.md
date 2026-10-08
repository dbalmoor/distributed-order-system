# Requirements and Future Work

This file is the backlog for requirements that are not yet implemented or do not
yet have sufficient verification. It complements the as-built architecture in
[design.md](design.md); it is not a description of current behavior.

## Implemented baseline

The repository currently includes the order, inventory, payment, orchestrator,
gateway, and shared DTO modules; persistent saga orchestration; transactional
outboxes; compensation; idempotency controls; saga deadlines and watchdog
recovery; operator recovery endpoints; bounded Kafka retry topics and DLT
replay; ADRs; and a failure-scenario matrix. See [design.md](design.md) for
implementation details and [docs/failure-matrix.md](docs/failure-matrix.md)
for what automated tests currently prove.

The work below remains pending. A requirement should be marked done only after
the implementation and its stated acceptance evidence are present.

## Phase 7 — Observability

### R12: Distributed tracing

Add OpenTelemetry spans across HTTP and Kafka boundaries, propagate trace
context in Kafka headers, and retain `sagaId` and `orderId` as useful
attributes. MDC correlation alone does not satisfy this requirement.

**Acceptance:** a documented or captured walkthrough shows one order trace
across the gateway and participating services, including Kafka hops.

### R13: Metrics and dashboards

Add Micrometer/Prometheus metrics for saga outcomes, step latency, outbox
backlog, consumer lag, retry/DLT counts, and duplicate-event counts. Provide a
Grafana dashboard.

**Acceptance:** a dashboard or documented walkthrough demonstrates a normal
flow and a failure/recovery flow using those metrics.

### R14: Structured audit logging

Provide consistent structured logs with correlation, saga, order, and step
fields, plus a searchable record of saga/order state transitions. Current MDC
`traceId` correlation is not an end-to-end observability solution.

**Acceptance:** representative logs show consistent fields across services
without exposing sensitive data.

## Remaining correctness and robustness work

### R15: Client idempotency for order creation

Add an `Idempotency-Key` header to `POST /orders`. Persist the key and request
fingerprint so a repeated request returns the original result, while reuse of
the same key with a different payload is rejected.

**Acceptance:** integration tests prove replay creates one order, returns the
original response, and rejects a mismatched request fingerprint.

### R16: Inventory reservation under contention

Current `InventoryServiceImpl.processReserve` obtains a pessimistic row lock
through `InventoryRepository.findByProductIdForUpdate`, checks
`availableQty`, and saves. The conditional SQL update currently applies to
release rather than reservation. Decide whether to retain and document this
approach or use an atomic conditional decrement; document trade-offs and any
bounded retry behavior.

**Acceptance:** a concurrency test with competing orders and limited stock
proves no oversell or lost reservation.

### R18: API hygiene

Add request validation and a consistent client error response format. Ensure
sensitive data is not written to logs. Full authentication and authorization
remain a separate security requirement and are not supplied by the current
`permitAll` gateway configuration.

**Acceptance:** API tests cover malformed and invalid requests and verify the
error response contract.

### Phase 2 test gaps

Add targeted verification for scenarios that currently lack dedicated
automated coverage:

- End-to-end transient Kafka failure followed by success, proving one business
  effect.
- Poison message routing to the DLT without blocking later messages in the
  partition.
- DLT replay idempotency.
- Duplicate inventory reserve command acknowledgement behavior.

**Acceptance:** Testcontainers tests exercise the actual retry, DLT, and replay
flows; keep the failure matrix synchronized with the resulting tests.

## Other future requirements

### R11: Chaos-lite recovery

Add a scripted test or runbook that stops a service during a saga, restarts it,
and demonstrates convergence to a consistent state.

**Acceptance:** the scenario is repeatable and records the resulting order and
saga states.

### R17: Reservation expiry behavior

The current watchdog uses saga deadlines and does not have a separate stock
TTL clock. Preserve that single clock unless a later requirement explicitly
changes the model. Expand verification if needed to prove that an unacknowledged
reserve is released after its applicable saga deadline.

**Acceptance:** a deterministic integration test advances or configures the
deadline and proves stock is restored without an independent inventory timer.

### Security and operations

- Add authentication and authorization before exposing gateway or direct
  service endpoints outside a trusted development environment.
- Admin saga recovery and DLT replay endpoints are currently unauthenticated
  and are not routed through the gateway. Secure them before production use.
- Define retention and cleanup for published outbox records and processed
  message/idempotency records.
- Add operational guidance for retry exhaustion, DLT inspection/replay, and
  `NEEDS_ATTENTION` resolution.

### Optional advanced work

- **Schema evolution:** evaluate versioned payloads or Avro/Protobuf with a
  schema registry and contract tests; the current services share `common-dto`.
- **Inventory benchmark:** compare contention approaches with measured
  throughput and tail latency before selecting a new reservation strategy.
- **Payment reconciliation:** model unknown provider outcomes, reconciliation,
  and duplicate/late provider callbacks if payment becomes real rather than a
  deterministic simulation.
- **Workflow-engine comparison:** document what a durable workflow engine
  provides relative to the current hand-rolled orchestrator.
- **Multi-instance exercise:** run multiple instances of each service and
  demonstrate safe partition handling, duplicate processing, and recovery.

## Definition of done

1. Every accepted requirement has implementation and automated evidence
   appropriate to its failure modes.
2. `mvn -B verify` passes from a clean checkout; integration tests run with
   Docker available.
3. Documentation describes the implementation actually present and separates
   completed behavior from pending work.
4. No exactly-once, no-loss, or security guarantee is claimed beyond what the
   implementation and tests establish.
