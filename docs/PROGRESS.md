# Progress
| Phase | Status | Notes |
|---|---|---|
| 0 Foundation (parent POM, CI, Flyway, compose) | Done | CI green on GitHub Actions |
| 1 Payment idempotency | Done | UNIQUE(saga_id, type) |
| 2 Single-writer order state machine | Done | 3 states: CREATED, COMPLETED, CANCELLED |
| 3 Transactional outbox | Done | order, inventory, payment, saga; inventory oversell proven by concurrency test (ADR 0004) |
| 4 Persistent saga state | Done | saga_instance, saga_step_log |
| 5 Compensation, timeouts, watchdog | Done | NEEDS_ATTENTION + admin retry/force-resolve |
| 6 Retry topics, DLT, replay | Done | tag v0.6-phase6; no automated retry/DLT/replay tests |
| 7a Metrics + log correlation | Done | Verified 2026-11-10; full reactor verification on 2026-11-11: 59 tests, 0 failures; success and failure walkthroughs + Prometheus screenshots in [evidence](evidence/order-120-e2e-2026-11-10.md) |
| 7b OpenTelemetry tracing | Done | Testcontainers verifies outbox trace propagation; Jaeger success trace verified across 4 services and 22 spans, and order 124 payment-failure/compensation trace across 4 services and 28 spans on 2026-11-11 |
| 8 README, ADRs, failure matrix | Done | refresh after Phase 7 |

## Next (in order)
No further Phase 7 work planned.

## Known gaps (keep README in sync)
- No tests: orchestrator crash/restart, duplicate inventory.reserve.cmd, poison-to-DLT and replay (DLT checked manually).
- No auth anywhere (gateway permitAll, admin/replay endpoints open).
- No client Idempotency-Key, no API error contract, replication factor 1.

Last full local build verification: 2026-11-11, `mvn -B verify` on JDK 17 — BUILD SUCCESS (59 tests, 0 failures). CI has not been checked and no commit hash is available yet. Runtime evidence: [order 120 success](evidence/order-120-e2e-2026-11-10.md), [order 121 failure](evidence/order-121-failure-2026-11-10.md), [order 122 success with Jaeger trace](evidence/order-122-e2e-2026-11-11.md), and [order 124 payment failure with compensation trace](evidence/order-124-payment-failure-2026-11-11.md).