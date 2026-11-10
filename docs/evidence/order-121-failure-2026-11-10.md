# Failure-path verification: order 121

**Date:** 2026-11-10  
**Order:** `121` (`ORD-75c03b80`)  
**Saga:** `07860ca1-882a-418a-92c2-67789f3908cb`  
**Result:** Passed; expected inventory failure was compensated and the order
was cancelled.

## Request

Submitted an online delivery order for one unit of product `999999`, which is
not present in the sample inventory database. The deliberately unknown product
caused the reserve attempt to fail.

## Observed outcome

- The order API initially returned `CREATED`, then `GET /orders/121` returned
  `CANCELLED`.
- Inventory recorded `RESERVE_FAILED`; its failure reason was
  `Product not found: 999999`.
- The saga step log recorded `order.created` (`ACTIVE`), `inventory.failed`
  (`ACTIVE` → `COMPENSATING`), `order.cancel.cmd`, and `order.cancelled`
  (`COMPENSATING` → `CANCELLED`).
- No payment row was created for order `121`; payment was not reached.
- The Prometheus counter `saga_cancelled_total` was `2` before this request and
  `3` afterward. `saga_completed_total` remained `1`, and
  `saga_needs_attention` was `0`.
- All four Prometheus scrape targets were `UP`.

Prometheus counters are process-lifetime totals, not order-specific audit
records. The before/after delta of one cancellation was observed during this
local walkthrough.

## Verification context

- Full reactor verification on 2026-11-10: `mvn -B verify` on Java 17 —
  BUILD SUCCESS, 58 tests, 0 failures.
- This is a manual local failure walkthrough, not a new automated E2E test.
