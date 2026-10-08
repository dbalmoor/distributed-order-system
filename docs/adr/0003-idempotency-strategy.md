# ADR 0003: Idempotency strategy

## Status

Accepted

## Context

Kafka delivery is at least once. Duplicate commands or events must not repeat a payment, stock mutation, or saga transition.

## Decision

Use business keys where available and message identity at the orchestrator:

- The `payments` table uses `UNIQUE(saga_id, type)`; repeated charge commands return the stored outcome.
- Inventory uses `processed_inventory_events` with `UNIQUE(saga_id, event_type)`.
- `RELEASED` and `REFUNDED` markers prevent a later reserve or charge after compensation arrives first.
- The orchestrator uses `processed_message` keyed by `(consumer_name, message_id)`.

Dedupe records and business effects are written in the same transaction.

## Consequences

Retries and duplicate delivery can re-emit the original outcome without repeating the business effect. The guards are colocated with the associated transaction and data.

## Rejected alternatives

A generic idempotency table for payments was rejected in favor of a unique business key on the `payments` row, which is simpler and shares the charge transaction.
