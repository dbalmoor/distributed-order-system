# Failure matrix

| Scenario | What happens | Expected state | Test | Proven by |
|---|---|---|---|---|
| Happy path | Full saga ends with `order.confirmed` | Order `COMPLETED`, saga `COMPLETED` | Happy-path integration test | `SagaPersistenceIntegrationTest#happyPathCompletesOnlyAfterOrderConfirmed` |
| Inventory reservation fails | `inventory.failed`, saga compensates, order cancelled | Order `CANCELLED`, saga `CANCELLED` | Inventory-failure test | `SagaPersistenceIntegrationTest#inventoryAndPaymentFailuresFinishCancelledOnlyAfterCancelAcknowledgment` |
| Payment fails | `payment.failed`, stock released, then order cancelled | Order `CANCELLED`, saga `CANCELLED` | Payment-failure test | `SagaPersistenceIntegrationTest#inventoryAndPaymentFailuresFinishCancelledOnlyAfterCancelAcknowledgment` |
| Crash between DB commit and Kafka publish | Outbox row stays NEW/IN_PROGRESS; poller publishes after restart | Flow continues, one effect | Outbox crash test | `OutboxIntegrationTest#committedMessageSurvivesPollerRestartAndPublishesOnce` |
| Orchestrator crash mid-saga | Saga row resumes at the last durable step; duplicates rejected | Terminal state reached | Restart test | NO TEST |
| Duplicate `payment.charge.cmd` | `UNIQUE(saga_id, type)` returns the stored result | One payment row | Duplicate-payment test | `PaymentIdempotencyIntegrationTest#duplicateCharge_reusesStoredSuccessfulPaymentAndOutcome` |
| Duplicate `inventory.reserve.cmd` | `processed_inventory_events` prevents a second stock mutation; the duplicate path currently returns without re-emitting the original acknowledgement | One reservation | Duplicate-inventory test | NO TEST |
| Duplicate event at the orchestrator | `processed_message` ignores it | No second command | Orchestrator dedupe test | `SagaPersistenceIntegrationTest#duplicateOrderCreatedAndMessageIdDoNotCreateAnotherSagaOrCommand` |
| Out-of-order event | State check rejects it, counted and logged | State unchanged | Out-of-order test | `SagaPersistenceIntegrationTest#outOfOrderEventLeavesSagaAndStepLogUnchanged` |
| Confirm not acknowledged | `order.confirm.cmd` retried with backoff, then `NEEDS_ATTENTION` | Saga `NEEDS_ATTENTION` | Confirm-retry test | `SagaPersistenceIntegrationTest#confirmTimeoutExhaustsRetriesAndAdminRetryAllowsLateConfirmation` |
| Inventory reservation times out | Watchdog sets `COMPENSATING`, sends `inventory.release.cmd`, waits for ack | Order `CANCELLED` after the ack | Timeout test | `SagaPersistenceIntegrationTest#reserveTimeoutReleasesInventoryAndCancelsAfterAcknowledgments` |
| Late `payment.success` after compensation | `LATE_SUCCESS` is logged and a refund is issued; a saga still waiting for compensation acknowledgements remains `COMPENSATING` | Saga `CANCELLED` after compensation completes; payment refunded | Late-success test | `SagaPersistenceIntegrationTest#latePaymentSuccessAfterCancellationQueuesOneRefund` |
| Release before reserve | `RELEASED` marker makes the later reserve fail | No stock held | Race test | `InventoryOutboxIntegrationTest#releaseBeforeReserveWritesMarkerAndPreventsLaterStockHold` |
| Refund before charge | `REFUNDED` marker makes the later charge fail | No charge | Race test | `PaymentIdempotencyIntegrationTest#refundBeforeChargeCreatesMarkerAndRejectsLaterCharge` |
| Cancel request after the pivot | Rejected; order continues to `COMPLETED` | Order `COMPLETED` | Cancel-too-late test | `SagaPersistenceIntegrationTest#cancelRequestsBeforePivotWaitForRequiredCompensationAcknowledgments` |
| Poison message | Routed to the DLT, replayed after review | Consumer not blocked | DLT test | NO TEST |

## Rows without automated test coverage

- Orchestrator process crash and recovery mid-saga
- Duplicate `inventory.reserve.cmd`
- Poison-to-DLT routing, non-blocking of later messages, and DLT replay
