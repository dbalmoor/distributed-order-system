$repo = "F:\PROJECTS\git\distributed-order-system"
Set-Location $repo
Copy-Item "$repo\design.md" "$repo\design.old.md" -Force
New-Item -ItemType Directory -Path "$repo\design-parts" -Force | Out-Null

$part01 = @'
# Design for the distributed order-management system

## 1. Summary and scope

This document defines the target design for the repository as it exists today, with the findings from the audit treated as the source of truth. The main corrective shift is to make the saga orchestrator the only authority that moves Order status, while each service remains idempotent under at-least-once Kafka delivery.

The design intentionally preserves existing service boundaries: `order-service`, `inventory-service`, `payment-service`, `saga-orchestrator`, `gateway-service`, and `common-dto`. The scope is correctness under failure, not a broad platform rewrite. The design is intentionally simple: state changes and outgoing messages are committed together through an outbox, consumers are idempotent, and the orchestrator persists saga state so it can resume after restart.

Key decisions already made and followed in this document:

- Pure orchestration: only the orchestrator drives Order status. Order-service reacts only to REST and to `order.confirm.cmd` / `order.cancel.cmd`.
- Outbox in every publishing service, using a poller with `FOR UPDATE SKIP LOCKED`.
- Persistent saga state: `OrderSaga` stores a String UUID `sagaId`, step log, deadline, and version.
- Payment idempotency uses `sagaId` as the natural key with a unique constraint and returns the stored result on repeat.
- Inventory uses a unique `(saga_id, event_type)` idempotency guard in the processed-event table, plus a new `inventory.released` event.
- Kafka keys are `orderId` on all topics; producers use `acks=all` and `enable.idempotence=true`.
- A repo-root parent/aggregator POM is added, with a GitHub Actions workflow.
- Gateway `permitAll` is explicitly documented as a known limitation and is not fixed in this phase.

This design is intentionally biased toward a boring, interview-safe architecture: transactional DB writes, explicit state transitions, and durable event publication, not optimistic assumptions about exactly-once processing.

## 2. As-is architecture

### Components

- `order-service`: owns Order domain state, REST API, and status transitions triggered by orchestrator commands.
- `inventory-service`: validates and reserves stock, emits `inventory.reserved` or `inventory.failed`, and later releases stock on compensating commands.
- `payment-service`: charges the customer, emits `payment.success` or `payment.failed`, and later performs refunds for compensating actions.
- `saga-orchestrator`: receives domain events and sends commands to drive the long-running saga.
- `gateway-service`: routes API traffic and is intentionally permissive in the current code.
- `common-dto`: shared command and event contracts used across services.

### Existing message flow (as audited)

The current implementation has a mix of event-driven and command-driven behavior. The orchestrator emits commands, but order-service also reacts directly to domain events. That is the root cause of status ambiguity and race-prone behavior. The design below removes that duplication by selecting a single writer.

```mermaid
flowchart LR
    Client[Client / REST] --> OrderSvc[order-service]
    OrderSvc -->|create order| Kafka[(Kafka)]
    Kafka -->|order.created| Orchestrator[saga-orchestrator]
    Orchestrator -->|inventory.reserve.cmd| Inventory[ inventory-service ]
    Inventory -->|inventory.reserved| Orchestrator
    Orchestrator -->|payment.charge.cmd| Payment[payment-service]
    Payment -->|payment.success| Orchestrator
    Orchestrator -->|order.confirm.cmd| OrderSvc
    Orchestrator -->|order.cancel.cmd| OrderSvc
    OrderSvc -->|status updates| DB[(PostgreSQL)]
    Inventory -->|inventory.failed| Orchestrator
    Payment -->|payment.failed| Orchestrator
```

### Data model and persistence observations

The current system already has the following persistence patterns in scope:

- Orders and inventory are stored in per-service PostgreSQL databases.
- Payment rows are stored in payment-service; inventory idempotency records and processed events are stored in inventory-service.
- The orchestrator contains `OrderSaga` as an entity, but the code does not actually use it as a persisted resume mechanism.
- Some services have `@Version` in the model layer, but not consistently across the critical write paths.
- The current code includes an `OutboxEvent` scaffold in order-service but does not actively use it for publication.

### Kafka topics and keys observed in the current code

The current implementation uses a set of event and command topics with inconsistent semantics. The target design below standardizes them around `orderId` as the message key for order-affinity ordering and consistent recovery.

- `order.created` / `order.confirmed` / `order.cancelled`
- `inventory.reserve.cmd` / `inventory.release.cmd`
- `inventory.reserved` / `inventory.failed` / `inventory.released`
- `payment.charge.cmd` / `payment.refund.cmd`
- `payment.success` / `payment.failed` / `payment.refunded`

## 3. Requirement status table

| Requirement | Status | Evidence |
|---|---|---|
| R0.1 Audit the as-is system | DONE | The repo contains an audit in `notes/audit.md` that is grounded in the checked-in code. |
| R0.2 One-command local environment | PARTIAL | Docker Compose is present for Kafka but does not yet cover the full system and database topology. |
| R0.3 Public REST API for orders | PARTIAL | REST routes exist, but there is no complete end-to-end demo script or verified runbook. |
| R1 Transactional outbox in every service that publishes | MISSING | Current code publishes directly to Kafka without a durable outbox transaction. |
| R2 Persistent saga state in the orchestrator | MISSING | `OrderSaga` exists, but there is no live persisted saga state or recovery path. |
| R3 Idempotent consumers in every service | PARTIAL | Inventory has a processed-event check; payment is not deduplicated by a natural business key. |
| R4 Kafka correctness configuration | PARTIAL | Producer/consumer config exists, but keying, offset semantics, topic creation, and retry policy are not yet consistent. |
| R5 Correct compensation semantics | PARTIAL | Compensation flows do not yet wait for specific compensating acks before final state changes. |
| R6 Explicit state machines | MISSING | Order transitions are ad hoc `if` checks, not a validated state machine. |
| R7 Timeouts and stuck-saga detection | MISSING | There is no deadline-driven timeout flow or compensating-action policy with late reply protection. |
| R8 Retry and DLQ strategy | PARTIAL | Retry/DLQ patterns exist in concept, but there is no consistent cross-service policy. |
| R9 Unit tests for state machines and logic | MISSING | The repo has light smoke/context tests, but no deterministic correctness test suite. |
| R10 Integration tests with Testcontainers | MISSING | There is no end-to-end Kafka/PostgreSQL saga failure suite. |
| R11 Chaos-lite | MISSING | There is no small fault-injection harness covering duplicates and timeouts. |
| R12 OpenTelemetry | MISSING | There is no explicit end-to-end tracing contract across Kafka and HTTP calls. |
| R13 Metrics | MISSING | There are no business-level metrics for saga age, outbox lag, or duplicates. |
| R14 Structured logging and audit trail | MISSING | Logs are not consistently structured around `traceId`, `sagaId`, and message IDs. |
| R15 Client Idempotency-Key | MISSING | No client idempotency contract exists for mutating requests. |
| R16 Inventory contention decision | PARTIAL | Inventory uses a pessimistic lock and may also have `@Version`, but the chosen canonical anti-over-sell rule is not yet explicit. |
| R17 Reservation TTL tied to the watchdog | MISSING | There is no reservation deadline tied to the saga watchdog. |
| R18 API hygiene | PARTIAL | API exists, but validation and error semantics are not yet standardized. |
| R19 README rewrite | MISSING | The README is not a trustworthy operational runbook. |
| R20 ADR list | MISSING | There is no maintained list of key architecture decisions. |
| R21 Failure matrix | MISSING | The repo does not yet include a failure matrix tied to the tested scenarios. |
| R22 Fix duplicate sendInventoryReserved | MISSING | The duplicate call is a known correctness bug and must be fixed with a regression test. |
| R23 Single-writer order status | MISSING | The order service currently mixes event-driven and command-driven status mutation. |
| R24 Parent/aggregator Maven build | MISSING | There is no root aggregator POM or CI-friendly full build target. |
| R25 String UUID `sagaId` everywhere | MISSING | The orchestrator state model still needs a consistent String UUID contract. |
| R26 Final cancel/fail outcome | MISSING | The system does not yet define one well-defined terminal state for compensated orders. |

## 4. Cross-service component view

```mermaid
flowchart LR
    Client[Client] --> Gateway[Gateway Service]
    Gateway --> Order[Order Service]
    Order --> OrderDB[(Order DB)]
    Order --> OrderOutbox[Order Outbox]
    OrderOutbox --> Broker[(Kafka)]

    Broker --> SagaConsumer[Orchestrator Consumer]
    SagaConsumer --> SagaState[(Saga State)]
    SagaConsumer --> SagaOutbox[Orchestrator Outbox]
    SagaOutbox --> Broker

    Broker --> InventoryConsumer[Inventory Consumer]
    InventoryConsumer --> InventoryDB[(Inventory DB)]
    InventoryConsumer --> InventoryOutbox[Inventory Outbox]
    InventoryOutbox --> Broker

    Broker --> PaymentConsumer[Payment Consumer]
    PaymentConsumer --> PaymentDB[(Payment DB)]
    PaymentConsumer --> PaymentOutbox[Payment Outbox]
    PaymentOutbox --> Broker
```

### 5.1 End-to-end behavior intended by this design

The intended end state is a single, boring saga: `CREATED` -> `COMPLETED` or `CANCELLED`, with `NEEDS_ATTENTION` as a non-terminal recovery state when a compensating or retry action cannot complete within budget. The system guarantees at-least-once delivery and idempotent business effects; it does not promise exactly-once semantics.

### 5.2 Happy path sequence

```mermaid
sequenceDiagram
    participant C as Client
    participant O as order-service
    participant K as Kafka
    participant S as saga-orchestrator
    participant I as inventory-service
    participant P as payment-service
    participant DB as PostgreSQL

    C->>O: POST /orders
    O-->>C: 202 Accepted
    O->>DB: insert Order(status=CREATED, displayStatus=PENDING)
    O->>K: outbox order.created
    K->>S: order.created
    S->>DB: persist saga state; mark step inventory.reserve
    S->>K: outbox inventory.reserve.cmd (orderId key)
    K->>I: inventory.reserve.cmd
    I->>DB: reserve stock + processed idempotency row
    I->>K: outbox inventory.reserved
    K->>S: inventory.reserved
    S->>DB: mark step payment.charge
    S->>K: outbox payment.charge.cmd
    K->>P: payment.charge.cmd
    P->>DB: insert Payment with UNIQUE(saga_id, type)
    P->>K: outbox payment.success
    K->>S: payment.success
    S->>DB: mark step complete
    S->>K: outbox order.confirm.cmd
    K->>O: order.confirm.cmd
    O->>DB: set Order.status=COMPLETED and emit order.confirmed outbox
```

### 5.3 Inventory failure path

```mermaid
sequenceDiagram
    participant C as Client
    participant O as Order Service
    participant S as Saga Orchestrator
    participant I as Inventory Service
    participant DB as PostgreSQL

    C->>O: POST /orders
    O->>DB: insert CREATED order
    O->>S: order.created
    S->>I: inventory.reserve.cmd
    I->>DB: stock unavailable; write inventory.failed
    I-->>S: inventory.failed
    S->>DB: mark step compensating
    S->>O: order.cancel.cmd
    O->>DB: set status=CANCELLED and emit order.cancelled outbox
    O-->>C: 202 Accepted; status becomes CANCELLED when polled
```

### 5.4 Payment failure with release-then-cancel

```mermaid
sequenceDiagram
    participant S as Saga Orchestrator
    participant I as Inventory Service
    participant P as Payment Service
    participant O as Order Service
    participant DB as PostgreSQL

    S->>I: inventory.reserve.cmd
    I->>DB: reserve stock
    I-->>S: inventory.reserved
    S->>P: payment.charge.cmd
    P->>DB: insert Payment row, mark payment.failed
    P-->>S: payment.failed
    S->>DB: mark step compensating
    S->>I: inventory.release.cmd
    I->>DB: release stock; emit inventory.released
    I-->>S: inventory.released
    S->>O: order.cancel.cmd
    O->>DB: set status=CANCELLED and emit order.cancelled outbox
```

### 5.5 Late payment.success for a saga already compensating or cancelled

```mermaid
sequenceDiagram
    participant S as Saga Orchestrator
    participant P as Payment Service
    participant O as Order Service
    participant I as Inventory Service
    participant DB as PostgreSQL

    Note over S: payment.success arrives while the saga is already COMPENSATING or CANCELLED
    S->>P: payment.charge.cmd
    P->>DB: payment inserted and success emitted
    P-->>S: payment.success
    S->>DB: mark saga as COMPENSATING; confirm step is invalid
    S->>P: payment.refund.cmd
    P->>DB: refund row written and payment.refunded emitted
    P-->>S: payment.refunded
    S->>I: inventory.release.cmd
    I->>DB: release stock and emit inventory.released
    I-->>S: inventory.released
    S->>O: order.cancel.cmd
    O->>DB: set status=CANCELLED and emit order.cancelled outbox
```

### 5.6 Timeout handling with compensating command and late reply safety

```mermaid
sequenceDiagram
    participant S as Saga Orchestrator
    participant I as Inventory Service
    participant P as Payment Service
    participant O as Order Service
    participant DB as PostgreSQL

    S->>I: inventory.reserve.cmd
    I-->>S: no reply within deadline
    S->>DB: mark saga as COMPENSATING
    S->>I: inventory.release.cmd
    I->>DB: inventory.release writes the RELEASED marker; emit inventory.released
    I-->>S: inventory.released
    S->>O: order.cancel.cmd
    O->>DB: set status=CANCELLED and emit order.cancelled outbox
    Note over S: late inventory.reserved arrives later
    I-->>S: late inventory.reserved
    S->>DB: reject state transition because compensation is already in progress; count duplicate and do not apply side effects
    Note over S: same rule applies for payment.refund.cmd arriving before payment.charge.cmd: the later charge is rejected when the saga is already in release/compensation state
```
'@
Set-Content "$repo\design-parts\part-01.txt" -Value $part01 -Encoding UTF8

$part02 = @'
### R1. Transactional outbox in every service that publishes to Kafka

Problem in the current code
- The existing services publish directly to Kafka from business methods instead of writing a durable outbox row in the same transaction as the business write.
- The `OutboxEvent` scaffold is present in order-service but never drives publication in practice.

Chosen approach and alternatives
- Every publish path writes the business change and a single outbox row in the same DB transaction. A dedicated poller then picks rows and sends Kafka messages.
- The outbox row includes `message_id`, `aggregate_type`, `aggregate_id`, `event_type`, `payload`, `headers`, `status`, `attempt_count`, `lease_until`, `created_at`, `published_at`, `last_error`.
- Poller locking is: `SELECT ... FOR UPDATE SKIP LOCKED` in a short claim transaction that sets `status=IN_PROGRESS` and `lease_until`; the message is published outside that claim and the row is then marked `PUBLISHED` or `FAILED`. A lease-expiry sweeper resets stuck rows so a crashed poller cannot hold them forever.
- Multiple pollers may reorder messages for the same order when they claim per `aggregate_id`, so the design guarantees per-aggregate ordering only when rows are processed in `created_at` order for one aggregate; it does not promise global ordering across all aggregates.
- Alternative considered: Debezium CDC. Rejected here because it adds operational complexity without being required to satisfy the repo’s correctness problem.

Data model changes
- Create an `outbox` table in each publishing service.
- DDL sketch: `message_id UUID PRIMARY KEY`, `aggregate_type TEXT NOT NULL`, `aggregate_id TEXT NOT NULL`, `event_type TEXT NOT NULL`, `payload JSONB NOT NULL`, `headers JSONB NOT NULL`, `status VARCHAR(16) NOT NULL DEFAULT 'NEW'`, `attempt_count INT NOT NULL DEFAULT 0`, `lease_until TIMESTAMP WITH TIME ZONE NULL`, `created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()`, `published_at TIMESTAMP WITH TIME ZONE NULL`, `last_error TEXT NULL`, `version BIGINT NOT NULL DEFAULT 0`.
- Composite index on `(status, created_at)` and `(aggregate_type, aggregate_id, created_at)`.
- Presence of `message_id` is required for dedupe and replay safety at the consumer boundary.

Kafka topics, keys, and headers
- Topics remain consistent with order, inventory, and payment domain events/commands; order-service also emits `order.confirmed` and `order.cancelled` through its outbox after state changes.
- Message key remains `orderId` for all order-related topics.
- Headers include `messageId`, `sagaId`, `orderId`, `traceId`, `eventType`.

Class-level changes
- Existing outbox classes are extended to carry a `messageId` and a small `OutboxPoller` that continuously polls and posts rows.
- Order-service writes outbox rows for both `order.created` and its later `order.confirmed` / `order.cancelled` events.

Transaction boundaries
- Business write + outbox insert happen in the same transaction.
- The Kafka publish happens after the commit, and the row is marked published or failed only after the producer call returns.
- Ordering guarantee is per aggregate: for a given `aggregate_id`, rows are processed in `created_at` order. Multi-poller concurrency can reorder messages across different aggregates; that is acceptable because the system is not assuming cross-aggregate global ordering.

Failure scenarios handled
- Crash after DB commit but before Kafka send: row remains in `NEW`/`IN_PROGRESS` and is re-sent by the poller.
- Poller crash: a lease or `FOR UPDATE SKIP LOCKED` prevents duplicate work.
- Producer fails: row stays not-published; retry increment occurs and the message is eventually retried.

How it will be tested
- Unit test: outbox write gets created with a unique `messageId` in the same transaction as the business row.
- Integration test: DB commit then crash before send, and verify eventual delivery with a single message.

### R2. Persistent saga state in the orchestrator

Problem in the current code
- `OrderSaga` is present but not the real source of truth; no live saga state is persisted for recovery.
- The orchestrator cannot resume after restart, nor can it reject out-of-order or duplicate events cleanly.

Chosen approach and alternatives
- Introduce `saga_instance` and `saga_step_log` tables and persist the saga’s state and step transitions in the same transaction as any command emission.
- Use `version` for optimistic concurrency and `deadline_at` for timeout handling.
- Alternative considered: replay from Kafka only. Rejected because the state would still be ambiguous after an unclean crash and late events would be impossible to reason about.

Data model changes
- `saga_instance` columns: `id BIGSERIAL PK`, `saga_id VARCHAR(36) UNIQUE NOT NULL`, `order_id BIGINT NOT NULL`, `status VARCHAR(32) NOT NULL`, `current_step VARCHAR(64) NOT NULL`, `deadline_at TIMESTAMP WITH TIME ZONE NULL`, `started_at TIMESTAMP WITH TIME ZONE NOT NULL`, `updated_at TIMESTAMP WITH TIME ZONE NOT NULL`, `version BIGINT NOT NULL DEFAULT 0`.
- `saga_step_log` columns: `id BIGSERIAL PK`, `saga_id VARCHAR(36) NOT NULL`, `step_name VARCHAR(64) NOT NULL`, `event_type VARCHAR(64) NOT NULL`, `from_state VARCHAR(32) NULL`, `to_state VARCHAR(32) NULL`, `payload JSONB NULL`, `created_at TIMESTAMP WITH TIME ZONE NOT NULL`.
- Indexes: `(saga_id, current_step)` and `(saga_id, event_type)`; no `created_at`-based index is needed for step log dedupe because duplicates are not inserted. This follows the requirement that duplicates are counted in a metric/log, not written to the step log.

Kafka topics, keys, and headers
- Orchestrator consumes `order.created`, `inventory.reserved`, `inventory.failed`, `inventory.released`, `payment.success`, `payment.failed`, `payment.refunded`, `order.confirmed`, and `order.cancelled`.
- Commands are emitted with `orderId` as the Kafka key and include `messageId`, `sagaId`, `orderId`, and `traceId` headers.

Class-level changes
- `SagaServiceImpl` is converted into a state-machine-backed service that validates transitions and persists the next state as part of the same transaction as the outbox insert.
- Add `OrderSagaRepository` and `SagaStepLogRepository`.
- The orchestrator dedupes incoming events with a `processed_message` table keyed by `(consumer, message_id)` and rejects invalid transitions before a business side effect is applied.

Transaction boundaries
- Reads the current saga state, validates the allowed step, updates the DB, inserts the outbox row, and only then commits. The offset is acknowledged only after the DB transaction returns successfully.

Failure scenarios handled
- Crash during a step: the persisted saga row resumes at the last durable step.
- Duplicate event: same `message_id` is recognized by the `processed_message` table and ignored; duplicate counts are recorded in metrics/logs.
- Out-of-order event: invalid for the current state and rejected without side effects.

How it will be tested
- Unit tests for explicit valid and invalid transitions.
- Restart test that crashes after DB write but before command send, then resumes and continues without duplicate side effects.

### R3. Idempotent consumers in every service

Problem in the current code
- Inventory has a processed-event table but payment is not deduplicated by a natural business key.
- Duplicate Kafka deliveries can cause duplicate charges, duplicate reservations, or duplicate refunds if idempotency is not enforced atomically with the business write.

Chosen approach and alternatives
- Payment dedupe is by the natural business key: `UNIQUE(saga_id, type)` on the `Payment` row; the service uses `INSERT ... ON CONFLICT DO NOTHING` and reads the stored payment result on repeat.
- Inventory dedupe is by `UNIQUE(saga_id, event_type)` on the inventory processed-event table; the service also uses `INSERT ... ON CONFLICT DO NOTHING` before applying an inventory mutation.
- The orchestrator dedupes by `processed_message` with `UNIQUE(consumer, message_id)`.
- Alternative considered: a separate `payment_idempotency` table was rejected in favour of a single, business-natural unique key on the `Payment` row; it is simpler and easier to reason about in the same transaction.

Data model changes
- `payment` table adds `saga_id VARCHAR(36)` and a unique constraint `UNIQUE(saga_id, type)`.
- `processed_inventory_event` table adds `saga_id VARCHAR(36) NOT NULL`, `event_type VARCHAR(64) NOT NULL`, `processed_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()`, plus `UNIQUE(saga_id, event_type)`.
- `processed_message` table for orchestrator dedupe: `consumer_name TEXT NOT NULL`, `message_id UUID NOT NULL`, `processed_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()`, `PRIMARY KEY (consumer_name, message_id)`.

Kafka topics, keys, and headers
- All commands/events retain `orderId` as the key and `sagaId` as the business correlation ID in the payload or headers.

Class-level changes
- Service consumer methods do the dedupe insert and the business write in the same transaction.
- Duplicate messages return the previous stored result instead of re-executing the business side effect.

Transaction boundaries
- The dedupe insert and business write occur in a single transaction, before the consumer offset is acknowledged.

Failure scenarios handled
- Duplicate `payment.charge.cmd`: the `INSERT ... ON CONFLICT DO NOTHING` succeeds or fails atomically; the service reads the stored result and emits the original outcome again if needed.
- Duplicate inventory event: same `saga_id` / `event_type` is ignored and the stored result is returned.

How it will be tested
- Integration tests for duplicate payment charge delivery, duplicate inventory reserve, and duplicate orchestrator message IDs.
'@
Set-Content "$repo\design-parts\part-02.txt" -Value $part02 -Encoding UTF8

$part03 = @'
### R4. Kafka correctness configuration

Problem in the current code
- Kafka configuration is not consistently aligned with the actual consumer behavior.
- The YAML shows `ack-mode: manual` but no listener calls `Acknowledgment.acknowledge()`, so in practice the configured behavior is not active.
- Producer keys and partition semantics are not standardized across all services.

Chosen approach and alternatives
- Standardize on `orderId` as the message key for every topic, including orchestrator commands and service-emitted events.
- Set producer config to `acks=all`, `enable.idempotence=true`, `retries` with bounded retry policy, and add `max.in.flight.requests.per.connection` tuned appropriately when idempotence is enabled.
- Use the default `RECORD` container `AckMode` and a `DefaultErrorHandler` with `@RetryableTopic` for transient failures; offsets are committed by the container only after the service method returns successfully and the transaction is complete.
- Explicit `NewTopic` beans create all required topics with sensible partitions. The YAML group IDs are reconciled with the `@KafkaListener` `groupId` values so that a given service family consumes the intended stream consistently.
- Alternative considered: leaving everything to Spring Boot defaults or using `MANUAL_IMMEDIATE` is rejected; the explicit requirement is to keep the offset commit semantics aligned with the transactional DB boundary and not to call `acknowledge()` manually.

Data model changes
- No schema-level DB change is required for the Kafka design alone; this design relies on stable keys and consumer-side safe commit semantics.

Kafka topics, keys, and headers
- Topics include `order.created`, `order.confirmed`, `order.cancelled`, `inventory.reserve.cmd`, `inventory.release.cmd`, `inventory.reserved`, `inventory.failed`, `inventory.released`, `payment.charge.cmd`, `payment.refund.cmd`, `payment.success`, `payment.failed`, `payment.refunded`, plus retry and DLQ topics named consistently by service and suffix (`-retry`, `-dlq`).
- Keys are always `orderId` to preserve ordering within each order stream.
- Headers include `messageId`, `sagaId`, `orderId`, `traceId`, `eventType`.

Class-level changes
- Add `@Bean NewTopic` declarations for the canonical topics and for retry/DLQ topics.
- Add `@KafkaListener` `groupId` values that match the YAML `spring.kafka.consumer.group-id` configuration for each service.
- Use `@RetryableTopic` for generic transient failures and keep a DLQ topic for poison messages.

Transaction boundaries
- The producer sends after the DB commit; the consumer processes the message, commits the business write and idempotency insert in the same transaction, and only then allows the Kafka container to commit the offset.

Failure scenarios handled
- Dead letter during a transient issue: `@RetryableTopic` retries with backoff.
- Permanent poison message: DLQ is used and the failure is visible in logs and metrics.
- Duplicate or replayed event: consumer idempotency rejects the duplicate before a side effect.

How it will be tested
- Integration tests verifying `acks=all`, topic creation, retry flow, and consistent group IDs across service startup.
'@
Set-Content "$repo\design-parts\part-03.txt" -Value $part03 -Encoding UTF8

$part04 = @'
### R5. Correct compensation semantics

Problem in the current code
- The previous design treated compensation as an afterthought and could silently ignore a late success or a lost confirm signal.
- The design must now be explicit: payment success is the pivot and the order is only confirmed after that success is durable.

Chosen approach and alternatives
- `payment.success` is the point of no return for the normal success path. Once payment is successful, the orchestrator may continue to the confirm step, but if `order.confirm.cmd` is not acknowledged, the orchestrator retries it idempotently with backoff and, if the retry budget is exhausted, moves the saga to `NEEDS_ATTENTION` rather than pretending the order is complete.
- A refund is only triggered for a real compensating scenario: a `payment.success` arriving after the saga has already moved to `COMPENSATING` or `CANCELLED` is late and therefore invalid; the orchestrator issues `payment.refund.cmd` and preserves the business effect of the refund.
- Alternative considered: “confirm fails, so refund” is rejected because the confirm step is a command to the order service, not a payment-side failure. The real and valid refund path is a late success after compensation is already underway.

Data model changes
- Add `saga_id` to the `Payment` row with a unique constraint and use `INSERT ... ON CONFLICT DO NOTHING` to prevent double charging.
- Add an explicit `inventory.released` event and a `released` idempotency marker keyed by `(saga_id, event_type)` to prevent a double release or a re-run of a compensating action.

Kafka topics, keys, and headers
- The normal path stays `order.created -> inventory.reserve.cmd -> inventory.reserved -> payment.charge.cmd -> payment.success -> order.confirm.cmd -> order.confirmed`.
- The compensation path is `inventory.release.cmd -> inventory.released` and `payment.refund.cmd -> payment.refunded` with `orderId` keys and explicit saga correlation in headers.

Class-level changes
- `SagaServiceImpl` must reject a confirm step when the saga is already `COMPENSATING` or `CANCELLED` and must emit the refund path instead of treating it as a normal success.
- The order-service emits `order.confirmed` and `order.cancelled` through its outbox; the orchestrator does not directly mutate Order status.

Transaction boundaries
- The dedupe insert and side effect occur in the same transaction before the offset is committed.
- The orchestrator sends a compensating command only after the saga state transition to `COMPENSATING` is committed.

Failure scenarios handled
- Unacknowledged confirm command: retry with backoff; if retries exhaust, move to `NEEDS_ATTENTION` but never silently claim success.
- Late `payment.success`: refund based on the valid compensation state and count the duplicate as a metrics event.
- Any duplicate reply after the compensation step: reject as invalid and emit a metric/log entry only.

How it will be tested
- Duplicate late success after compensation.
- Confirm command retries and escalation to `NEEDS_ATTENTION`.
- Refund path after a late success is idempotent and does not create a second refund.
'@
Set-Content "$repo\design-parts\part-04.txt" -Value $part04 -Encoding UTF8

$part05 = @'
### R6. Order state machine and single-writer status flow

Problem in the current code
- The current system mixes event-driven and command-driven order status mutations and has an ambiguous set of intermediate states.
- The target design collapses the state machine to a simpler and safer order lifecycle.

Chosen approach and alternatives
- Order status has exactly three states: `CREATED` (shown to clients as `PENDING`), `COMPLETED`, and `CANCELLED`.
- `INVENTORY_RESERVED` is removed from the public order model because the sequence of commands and events already encodes the reservation state in the saga and inventory DB, and the user-facing order state must stay minimal.
- The orchestrator is the only component that drives the order lifecycle via `order.confirm.cmd` and `order.cancel.cmd`; order-service reacts only to REST and to those commands, then emits `order.confirmed` / `order.cancelled` through its outbox.
- Alternative considered: retaining `FAILED` and the two `*_PENDING` states was rejected because they add ambiguity and are not needed when a saga can show `NEEDS_ATTENTION` as a recovery state outside the public order status tree.

Data model changes
- `Order.status` only accepts `CREATED`, `COMPLETED`, `CANCELLED`.
- `displayStatus` can still show `PENDING` while the order is created but not yet terminal.

Kafka topics, keys, and headers
- Order-service emits `order.confirmed` and `order.cancelled` through the outbox after it updates the DB row.
- The orchestrator is the consumer of the `order.confirmed` and `order.cancelled` domain events and completes the saga on those messages.

Transaction boundaries
- The order-service writes the status update and outbox row within one transaction, then commits. The Kafka publish occurs after the commit.

Failure scenarios handled
- Duplicate `order.confirm` or `order.cancel` command: the order-service validates the current state before writing and rejects invalid transitions.
- Late or repeated completion/cancel messages: the system ignores them after the terminal state is reached.

How it will be tested
- Unit tests for valid transitions and illegal transitions.
- Integration tests for order confirm/cancel actions emitted via the outbox and consumed by the orchestrator.
'@
Set-Content "$repo\design-parts\part-05.txt" -Value $part05 -Encoding UTF8

$part06 = @'
### R7. Timeouts and stuck-saga detection

Problem in the current code
- The repo has no explicit deadline or watchdog path for stalled work.
- When a step never responds, the system cannot safely distinguish “not yet complete” from “permanently failed”.

Chosen approach and alternatives
- A step times out when it exceeds its deadline without a durable reply. The orchestrator marks the saga as `COMPENSATING`, sends the compensating command for the timed-out step (`inventory.release.cmd` or `payment.refund.cmd`), and waits for the compensating acknowledge before proceeding.
- `NEEDS_ATTENTION` is a non-terminal recovery state used only when the compensation or retry path itself cannot be acknowledged within its retry budget; it is not the way a normal timeout is finalized.
- The orchestrator must reject late replies that imply a side effect when the saga is already in `COMPENSATING` or `CANCELLED`.
- Alternative considered: marking the saga `FAILED` and leaving the side effects unresolved was rejected because it is not a well-defined terminal state for compensating flows.

Data model changes
- Add `deadline_at`, `last_heartbeat_at`, and `version` to `saga_instance`; step log records are appended only for valid, durable transitions.
- Add a retry budget and a `needs_attention` flag on the step or saga record to allow manual admin recovery.

Kafka topics, keys, and headers
- Unknown-outcome timeouts trigger `inventory.release.cmd` or `payment.refund.cmd` with the same `orderId` key and the current `sagaId` in headers.
- Later replies are ignored after the state becomes compensating, but still counted and logged as duplicates.

Class-level changes
- Add a watchdog job that scans for timed-out sagas and transitions them to `COMPENSATING` atomically with the compensating command emission.
- Add an admin endpoint or CLI command to retry or force-resolve a saga stuck in `NEEDS_ATTENTION`.

Transaction boundaries
- The saga state transition to `COMPENSATING` and the compensating outbox row are inserted in the same transaction.
- The command is not considered complete until the service acknowledges the compensating command.

Failure scenarios handled
- Inventory never replies: trigger `inventory.release.cmd` when the reservation is still pending and wait for the ack.
- Payment never replies: trigger `payment.refund.cmd` when the payment step is uncertain and wait for the ack.
- Late reply after compensation: reject, count, and log without side-effect.

How it will be tested
- Timed-out inventory reserve and payment charge flows.
- Late `inventory.reserved` or `payment.success` arriving after compensation begins.
- `NEEDS_ATTENTION` recovery path through the admin retry/resolve API.
'@
Set-Content "$repo\design-parts\part-06.txt" -Value $part06 -Encoding UTF8

$part07 = @'
### R8. Retry and DLQ strategy

Problem in the current code
- Retries and dead-letter behavior exist only conceptually; they are not formalized as a consistent contract across the services.

Chosen approach and alternatives
- Use `@RetryableTopic` for transient failures with backoff and a DLQ topic for poison messages.
- Retry only for classes of exceptions that are known to be transient (timeouts, temporary dependency issues, optimistic locking conflicts, transient network failures). Permanent validation failures or poison payloads go directly to the DLQ.
- Keep DLQ messages with original headers plus failure reason and stack information. A replay script or admin endpoint allows re-injecting a message after review, with idempotent consumers making replay safe.
- Alternative considered: a `dlq_message` table was rejected because the repo already has a clear Kafka-topic-based DLQ contract and a replay script is simpler to operate and reason about.

Data model changes
- No separate `dlq_message` table is required. The DLQ topic is the durable source of truth for replay.

Kafka topics, keys, and headers
- Topics include `*-retry`, `*-dlq`, plus the main topics. The same `orderId` key is preserved in replay so the order stream remains predictable.

Class-level changes
- Add `@RetryableTopic` and a `DefaultErrorHandler` on each listener.
- Expose a small replay script or admin endpoint that reads from the DLQ topic and re-sends the original payload after an operator review.

Transaction boundaries
- The consumer must finish the business write and idempotency insert before offset commit, otherwise retries could duplicate work.

Failure scenarios handled
- Transient dependency outage: retry with backoff and eventual success.
- Poison message: immediate DLQ routing.
- Replay: safe because the consumers are idempotent and reject duplicates by natural business key or `message_id`.

How it will be tested
- Retry then success path.
- Poison message to DLQ.
- Replay of a previously failed message with idempotent repeated processing.
'@
Set-Content "$repo\design-parts\part-07.txt" -Value $part07 -Encoding UTF8

$part08 = @'
### R9. Unit tests

- State machine tests for order lifecycle and saga transitions.
- Idempotency tests for `Payment` by `(saga_id, type)` and inventory by `(saga_id, event_type)`.
- Money and rounding tests for `BigDecimal` payments and refunds.
- Orchestrator decision tests for timeout, compensation, and late-reply rejection.

### R10. Testcontainers

- Kafka + PostgreSQL integration suite covering the happy path, inventory failure, payment failure, duplicate delivery, out-of-order events, crash/restart, and timeout scenarios.
- The stack must run under a single test harness and isolate each scenario without relying on local developer state.

### R11. Chaos-lite

- A small fault-injection harness that stops one service mid-saga, restarts it, and proves the system converges to a consistent terminal state without duplicate side effects.
- Include the scenarios that simulate lost acknowledgements, delayed replies, and replays after restart.

### R12. OpenTelemetry

- Spans across HTTP and Kafka; `traceId`, `sagaId`, `orderId`, and the component name are propagated through message headers and MDC.
- `tempo` or `jaeger` is included in the local stack for end-to-end troubleshooting.

### R13. Metrics

- Micrometer + Prometheus counters and gauges for saga starts, completions, compensations, retries, DLQ count, duplicate-event count, outbox backlog, and step latency.
- Business-level metrics are part of the required correctness proof, not an afterthought.

### R14. Structured logging and audit trail

- Every service logs `traceId`, `sagaId`, `orderId`, `messageId`, and state transition metadata in a consistent JSON or structured format.
- A persisted audit trail stores the state transitions for every saga and every order step.

### R15. Client Idempotency-Key

- `POST /orders` accepts an `Idempotency-Key` header.
- Repeating the same key with the same payload returns the original result; repeating with a different payload is rejected as a conflict.

### R16. Inventory contention decision

- Primary approach: atomic conditional update is the canonical anti-over-sell mechanism.
- Example: `UPDATE inventory SET available = available - :qty WHERE product_id = :id AND available >= :qty`.
- The pessimistic lock remains as a fallback when the DB or domain needs a stronger lock, but it is not the primary solution.
- `@Version` may remain for optimistic locking or concurrency detection, but it does not replace the atomic conditional update as the guard for oversell prevention.

### R17. Reservation TTL tied to the watchdog

- Reserved stock is released automatically when a saga exceeds its reservation deadline.
- The watchdog and reserving service must share the same timeout policy and be coordinated around the same deadline clock.

### R18. API hygiene

- Validate request bodies, standardize error payloads, and ensure no sensitive data is logged.
- This phase keeps authentication and authorization outside the core correctness work but documents them as known limitations.

### R19. README rewrite

- Remove unverified claims and replace them with a tested runbook, diagrams, configuration notes, and a known-limitations section.
- Include how to run the stack, how to reproduce the failure scenarios, and how to inspect Kafka and DB metrics.

### R20. ADR list

- Add `docs/adr` with decisions for orchestration, outbox, idempotency, inventory contention, retry/DLQ, and the use of a shared DTO module.
- Each ADR should state the decision, the context, the consequences, and the rejected alternatives.

### R21. Failure matrix

- A scenario table stating: issue, observable effect, compensating action, expected status, and which automated test proves it.
- Include late `payment.success`, release-before-reserve race, timed-out inventory reservation, timed-out payment charge, and poison message handling.

### R22. Fix the duplicate sendInventoryReserved call

- Fix the duplicate `sendInventoryReserved` call in `InventoryServiceImpl.processReserve` and add a regression test covering duplicate emission under the success flow.
- The current code must prove the service emits only one inventory-reserved message for one successful reservation.

### R23. Single writer of Order status

- Decide on one status-writer policy only: the order-service reacts only to REST and orchestrator commands, never to both domain events and commands.
- Document the decision and enforce it in the code and in the integration tests.

### R24. Parent/aggregator POM

- Add a root parent/aggregator POM so `mvn verify` builds and tests every module from a single command.
- Include the shared DTO module and the GitHub Actions workflow in the same build path.

### R25. `sagaId` is a String UUID everywhere

- Use `String sagaId` consistently across the orchestrator, payment, inventory, and order models.
- Remove any mismatched type or implicit conversion and document the contract in the DTOs and DB schema.

### R26. Final compensated order state

- Define one terminal compensating outcome for an order that is cancelled or compensated: `CANCELLED` is the canonical state, and the system must not leave a compensated order in a fuzzy or half-failed state.
- Document the rule and prove it with the failure matrix and the timeout/retry integration tests.
'@
Set-Content "$repo\design-parts\part-08.txt" -Value $part08 -Encoding UTF8

$part09 = @'
## 7. State machines

### Order state machine

`CREATED` -> `COMPLETED` or `CANCELLED`.

- `CREATED`: initial order accepted and shown to clients as `PENDING`.
- `COMPLETED`: terminal success state reached only after the orchestrator has observed the successful payment and the order-service has written the completed status.
- `CANCELLED`: terminal compensation outcome reached after inventory release or payment refund completes.

No public-order `FAILED` or `INVENTORY_RESERVED` state is kept; those are internal or transient saga details, not reliable client-facing status.

### Saga state machine

- `ACTIVE`: normal in-flight saga.
- `COMPENSATING`: one step timed out or a late reply invalidated the confirm path; the orchestrator is issuing compensating commands.
- `CANCELLED`: the saga reached a compensating terminal state after successful release/refund or an admin resolve.
- `NEEDS_ATTENTION`: non-terminal recovery state when a compensating or retry step exceeds its retry budget and needs manual or admin intervention.

Transitions across these states are validated and logged; invalid transitions are rejected before a new side effect is applied.

## 8. Kafka topics, keys, and contracts

| Topic | Producer | Key | Consumer | Notes |
|---|---|---|---|---|
| `order.created` | order-service | `orderId` | saga-orchestrator | Initial creation event |
| `order.confirmed` | order-service | `orderId` | saga-orchestrator | Confirms terminal success |
| `order.cancelled` | order-service | `orderId` | saga-orchestrator | Confirms terminal cancellation |
| `inventory.reserve.cmd` | saga-orchestrator | `orderId` | inventory-service | Reserve stock |
| `inventory.release.cmd` | saga-orchestrator | `orderId` | inventory-service | Compensating release |
| `inventory.reserved` | inventory-service | `orderId` | saga-orchestrator | Successful reservation |
| `inventory.failed` | inventory-service | `orderId` | saga-orchestrator | Reservation failure |
| `inventory.released` | inventory-service | `orderId` | saga-orchestrator | Release acknowledged |
| `payment.charge.cmd` | saga-orchestrator | `orderId` | payment-service | Payment request |
| `payment.refund.cmd` | saga-orchestrator | `orderId` | payment-service | Compensation request |
| `payment.success` | payment-service | `orderId` | saga-orchestrator | Payment pivot |
| `payment.failed` | payment-service | `orderId` | saga-orchestrator | Payment failure |
| `payment.refunded` | payment-service | `orderId` | saga-orchestrator | Refund acknowledged |
| `order-retry` | internal | `orderId` | service listeners | Transient retry topic |
| `order-dlq` | internal | `orderId` | admin replay script | Poison message sink |

Notes:
- Every topic uses `orderId` as the key for order affinity and stable replay ordering.
- `order.confirmed` and `order.cancelled` are emitted by order-service through the outbox after the status update commit.
- The DLQ and retry topics are explicit `NewTopic` beans, not implicit or ad-hoc topics.

## 9. Failure matrix

| Scenario | What happens | Expected state | Evidence/test |
|---|---|---|---|
| Payment succeeds, confirm unacked | `order.confirm.cmd` is retried idempotently with backoff; if retries fail, saga goes to `NEEDS_ATTENTION` | `NEEDS_ATTENTION` | retry integration test |
| Late `payment.success` after compensation | The message is invalid for the current saga state; orchestrator issues `payment.refund.cmd` and continues cancel path | `CANCELLED` | late-success test |
| Inventory reservation times out | Orchestrator asks for `inventory.release.cmd`; if the step never replies it stays in `COMPENSATING` and may escalate to `NEEDS_ATTENTION` | `COMPENSATING` / `NEEDS_ATTENTION` | timeout test |
| Release-before-reserve race | `inventory.release.cmd` writes a `(saga_id, RELEASED)` marker; a later `inventory.reserve.cmd` for the same saga is rejected | idempotent rejection | race test |
| Payment refund before charge | `payment.refund.cmd` is rejected if the charge never executed, and the saga records the invalid late-compensation event | `NEEDS_ATTENTION` or `CANCELLED` by policy | race test |
| Poison message | Message goes to DLQ and is replayed only after review | DLQ / safe replay | DLQ test |

## 10. Test strategy

- Unit tests exercise the state machine and decision logic.
- Integration tests use Testcontainers for Kafka and PostgreSQL.
- Chaos-lite tests simulate service restarts, delayed replies, and duplicate message delivery.
- Each phase of the rollout ships with a concrete test module proving the behavior.

## 11. Rollout plan

### Phase 0 — baseline and buildability
- Parent/aggregator POM, GitHub Actions, Flyway baseline, full Docker Compose, and test harness.
- Tests: module build smoke, Docker Compose startup, default configuration checks.

### Phase 1 — payment idempotency and deterministic failure rule
- Add `saga_id` to `Payment` with `UNIQUE(saga_id, type)`, use `INSERT ... ON CONFLICT DO NOTHING`, and add a deterministic, configurable failure rule.
- Tests: duplicate-charge test, deterministic failure rule test.

### Phase 2 — single-writer order state machine
- Replace dual-writing with pure orchestration and emit `order.confirmed` / `order.cancelled` through the outbox.
- Tests: state-machine valid/invalid transition tests, outbox-emission integration tests.

### Phase 3 — transactional outbox
- Add `outbox` tables to all publishing services and the poller with claim + lease logic.
- Tests: DB commit + publish crash recovery, outbox replay, duplicate send prevention.

### Phase 4 — persistent saga state
- Persist `saga_instance` and `saga_step_log`, version, and deadline data. Make handlers idempotent and reject invalid late events.
- Tests: restart mid-saga, duplicate message, invalid transition rejection.

### Phase 5 — compensation, timeouts, watchdog
- Add compensation commands, stepping to `COMPENSATING`, timeout policy, and the admin recovery path.
- Tests: timeout-to-compensation, late-reply rejection, release-before-reserve race, admin retry/resolve.

### Phase 6 — retry and DLQ
- Add `@RetryableTopic`, DLQ topics, and a replay script.
- Tests: retry-then-success, poison-to-DLQ, replay-safe implementation.

### Phase 7 — observability
- OpenTelemetry, structured logging, metrics, and Grafana dashboards.
- Tests: trace correlation across HTTP/Kafka and dashboard data validation.

### Phase 8 — docs and ADRs
- README rewrite, ADR set, and failure matrix.
- Tests: documentation check for claims that are backed by automated tests.

## 12. Top 5 uncertainties

1. Whether the repo will run with shared DBs or one database per service in production.
2. Whether `FAILED` must remain as a historical status for the sake of reporting or is intentionally removed from the public state machine.
3. Whether the gateway will remain intentionally permissive during the correctness-first implementation or whether security hardening is scheduled in a later phase.
4. Whether the project wants a full Docker Compose environment or a lighter local test harness for developers.
5. Whether the team prefers a single service-owned `outbox` table per module or a shared outbox schema pattern.

## 13. Implementation notes

- This design chooses boring correctness over cleverness: at-least-once plus idempotent consumers, explicit state transitions, and durable outbox publication are the core invariants.
- The system is intentionally conservative about exactly-once guarantees and makes no claim beyond the tested behavior.
- `NEEDS_ATTENTION` remains a recovery state, not a terminal public order status.
'@
Set-Content "$repo\design-parts\part-09.txt" -Value $part09 -Encoding UTF8

$parts = @(
  "$repo\design-parts\part-01.txt",
  "$repo\design-parts\part-02.txt",
  "$repo\design-parts\part-03.txt",
  "$repo\design-parts\part-04.txt",
  "$repo\design-parts\part-05.txt",
  "$repo\design-parts\part-06.txt",
  "$repo\design-parts\part-07.txt",
  "$repo\design-parts\part-08.txt",
  "$repo\design-parts\part-09.txt"
)
Set-Content "$repo\design.v2.md" -Value "" -Encoding UTF8
foreach ($file in $parts) {
    Get-Content -Raw -Path $file | Add-Content "$repo\design.v2.md" -Encoding UTF8
}
Copy-Item "$repo\design.v2.md" "$repo\design.md" -Force
Write-Host "design.v2.md created and design.md replaced"