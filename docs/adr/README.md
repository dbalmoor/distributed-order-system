# Architecture decision records

| ADR | Decision |
|---|---|
| [0001 — Saga orchestration](0001-saga-orchestration.md) | Persist saga state and coordinate steps and compensation in the orchestrator. |
| [0002 — Transactional outbox](0002-transactional-outbox.md) | Commit business changes and outgoing messages together, then publish asynchronously. |
| [0003 — Idempotency strategy](0003-idempotency-strategy.md) | Use message or natural business keys to deduplicate effects atomically. |
| [0004 — Inventory contention](0004-inventory-contention.md) | Use an atomic conditional update as the primary oversell guard. |
| [0005 — Retry and DLQ](0005-retry-and-dlq.md) | Retry transient Kafka failures and route poison messages to DLTs. |
| [0006 — Shared DTO module](0006-shared-dto-module.md) | Keep shared message contracts in `common-dto` in the root reactor. |
| [0007 — Single-writer order status](0007-single-writer-order-status.md) | Let orchestrator commands alone cause order-status changes. |
