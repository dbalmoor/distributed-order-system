# ADR 0001: Saga orchestration

## Status

Accepted

## Context

Order processing spans services with separate databases and no distributed transaction. The orchestrator needs durable workflow state to recover after a restart and to handle duplicate or out-of-order events safely.

## Decision

Persist saga state in `saga_instance` and transitions in `saga_step_log`. `SagaServiceImpl` coordinates explicit state and step transitions, persisting state changes with outgoing outbox commands. Events for one saga are serialized by locking its `saga_instance` row; `version` remains an optimistic concurrency safety net. Payment success is the pivot to confirmation. An unacknowledged `order.confirm.cmd` is retried and escalated to `NEEDS_ATTENTION` if its retry budget is exhausted.

## Consequences

The orchestrator can resume from the last committed step and reject invalid transitions before issuing another command. Compensation and recovery are explicit workflow paths.

## Rejected alternatives

Replaying Kafka as the sole source of saga state was rejected because state can be ambiguous after an unclean crash and late events are difficult to reason about.
