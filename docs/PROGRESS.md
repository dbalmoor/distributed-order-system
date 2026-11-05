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
| 7a Metrics + log correlation | Not started | |
| 7b OpenTelemetry tracing | Not started | optional |
| 8 README, ADRs, failure matrix | Done | refresh after Phase 7 |

## Next (in order)
1. Phase 7a (metrics + log correlation).
2. Phase 7b only if time allows.

## Known gaps (keep README in sync)
- No tests: orchestrator crash/restart, duplicate inventory.reserve.cmd, poison-to-DLT and replay (DLT checked manually).
- No auth anywhere (gateway permitAll, admin/replay endpoints open).
- No client Idempotency-Key, no API error contract, replication factor 1.

Last verified: pending full verification and commit.