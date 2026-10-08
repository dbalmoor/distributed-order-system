# ADR 0002: Transactional outbox

## Status

Accepted

## Context

Publishing directly to Kafka from business methods can lose a message if a process stops after its database transaction commits but before Kafka accepts the message.

## Decision

Each Kafka-publishing service writes its business update and an `outbox` row in one database transaction. Its `OutboxPoller` claims rows with `SELECT ... FOR UPDATE SKIP LOCKED`, publishes after the claim transaction, and records the publish result. Rows for one aggregate are processed in `created_at` order; a failed row blocks later rows for that aggregate.

## Consequences

A committed outbox row survives process restarts and can be published later. Delivery remains at least once, so consumers must be idempotent. Ordering is per aggregate, not global across orders.

## Rejected alternatives

Debezium CDC was rejected because it adds operational complexity without being required to address the repository's correctness problem.
