# ADR 0004: Inventory contention

## Status

Accepted in the design; implementation gap documented below

## Context

Concurrent reservations must not oversell a product. A lock or version counter alone does not express the stock-availability condition at the point of mutation.

## Decision

R16 selects an atomic conditional update as the canonical oversell guard, equivalent to `UPDATE inventory SET available = available - :qty WHERE product_id = :id AND available >= :qty`. A pessimistic lock may remain as a fallback when stronger locking is needed. `@Version` may remain for optimistic-lock detection, but does not replace the conditional update as the primary guard.

## Consequences

When implemented for reservation, the stock check and decrement will be one database operation. **Current implementation differs:** `InventoryServiceImpl.processReserve` loads each row through `InventoryRepository.findByProductIdForUpdate`, checks `availableQty`, and saves the entity. `InventoryRepository.releaseReservation` is a conditional SQL update for release, not reservation. This discrepancy is not represented as an implemented R16 decision.

## Rejected alternatives

Using `@Version` as the sole oversell-prevention mechanism was rejected; it does not replace the atomic conditional update.
