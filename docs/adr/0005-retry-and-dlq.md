# ADR 0005: Retry and DLQ

## Status

Accepted

## Context

Kafka consumers encounter transient dependency failures and permanent invalid messages. Retrying every failure indefinitely can block useful work, while dropping transient failures can lose progress.

## Decision

Use bounded `@RetryableTopic` retries with configurable backoff for selected transient failures and a `-dlt` topic for poison messages. Keep the key and original headers, retain failure information, and replay only after operator review. Use RECORD acknowledgement mode and align each listener's group ID with its configured consumer group. Each service's `KafkaTopicsConfig` declares main, retry, and DLT topics.

## Consequences

Transient failures can be retried on retry topics; permanent or unlisted failures are sent to a DLT for inspection. Retry-topic reordering is handled through saga state checks and the inventory `RELEASED` and payment `REFUNDED` markers. Delivery and replay remain at least once. Current tests verify startup configuration and topic creation, but do not prove retry delivery, poison-to-DLT handling, or replay behavior end to end.

## Rejected alternatives

- Manual acknowledgement was rejected because RECORD mode commits only after the listener returns successfully and the transaction is complete.
- A database table of failed messages was rejected because the DLT is already durable and a replay endpoint is simpler to operate.
