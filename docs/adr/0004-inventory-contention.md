# ADR 0004: Inventory contention

## Status

Accepted

## Context

Concurrent reservations must not oversell a product. A lock or version counter alone does not express the stock-availability condition at the point of mutation.

## Decision

Reserve each order item with one atomic conditional update:
`UPDATE inventory SET available_qty = available_qty - :qty, reserved_qty = reserved_qty + :qty WHERE product_id = :id AND available_qty >= :qty`.
Process items in ascending product ID order. If any item cannot be reserved, restore the earlier item updates in the same transaction, then commit the failure marker and `inventory.failed` outbox row. The `version` column is incremented by the update.

## Consequences

The stock-availability check and decrement happen atomically at the database. Sorting product IDs avoids inconsistent row-lock order across multi-item reservations. The integration test races 50 distinct reservations against stock of 10 and verifies exactly 10 reservations succeed without a negative balance.

## Rejected alternatives

Using `@Version` as the sole oversell-prevention mechanism was rejected; it detects concurrent changes but does not itself enforce the availability condition in the update.
