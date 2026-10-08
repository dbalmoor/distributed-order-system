# ADR 0007: Single-writer order status

## Status

Accepted

## Context

Multiple writers of order status make transitions ambiguous. Order status should reflect orchestrated business decisions, not intermediate events from other services.

## Decision

The orchestrator decides order transitions by emitting `order.confirm.cmd` or `order.cancel.cmd`; `order-service` alone writes the status while handling those commands. REST creates orders and records cancel requests but does not directly change status. The lifecycle is `CREATED`, `COMPLETED`, or `CANCELLED`.

## Consequences

Status transitions are enforced by the order state machine and emitted as `order.confirmed` or `order.cancelled` through the outbox. Reservation and payment progress remain in their service state and the saga.

## Rejected alternatives

- Allowing domain events as additional order-status writers was rejected in favor of one command-driven writer.
- Retaining `FAILED` and intermediate `INVENTORY_RESERVED` and `PAYMENT_*_PENDING` order statuses was rejected because the saga tracks progress and `NEEDS_ATTENTION` provides a separate recovery state.
