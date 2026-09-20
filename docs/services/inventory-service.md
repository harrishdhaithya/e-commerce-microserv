# inventory-service

> 🔵 **Planned — Phase 2. Not implemented.** This is a design specification. Nothing
> described here exists yet, and details will change on contact with real code.

Stock levels and reservations. The service where concurrency actually matters — two
customers must not both buy the last item.

| | |
|---|---|
| **Port** | 8083 |
| **Resources** | `/api/stock`, `/api/reservations` |
| **Database** | `jdbc:h2:file:./data/inventory` |
| **Module** | `services/inventory-service` (planned) |
| **Auth** | `ADMIN` for writes; availability reads public |

## Responsibilities

**Owns:** stock quantity per SKU, reservations held during checkout, and an append-only
ledger of every movement.

**Does not own:** product metadata (catalog-service). Inventory keys on **`sku`**, a
string it treats as opaque — no foreign key across the service boundary, and no
assumption that catalog-service is reachable.

## The concurrency model

This is the part worth getting right.

**Reservation, not decrement.** Checkout *reserves* stock, and only a confirmed
payment *commits* it. A failed or abandoned checkout releases the reservation. A
straight decrement at checkout would strand stock on every abandoned cart.

**Optimistic locking, not `SELECT … FOR UPDATE`.** A `@Version` column with a retry
on conflict. Chosen because it is JPA-level and behaves identically on every
database, whereas pessimistic lock semantics differ between H2, PostgreSQL and
Oracle. The cost is handling `OptimisticLockException` with a bounded retry; the
benefit is that the concurrency behaviour you learn here transfers.

**Never read-then-write without one of the two.** The classic oversell bug.

## Database schema

### `stock_items`

| Column | Type | Constraints |
|---|---|---|
| `id` | `BIGINT` | PK, identity |
| `sku` | `VARCHAR(64)` | `NOT NULL`, unique |
| `quantity_on_hand` | `INTEGER` | `NOT NULL`, `CHECK >= 0` |
| `quantity_reserved` | `INTEGER` | `NOT NULL`, `CHECK >= 0` |
| `reorder_level` | `INTEGER` | `NOT NULL`, default `0` |
| `updated_at` | `TIMESTAMP` | `NOT NULL` |
| `version` | `INTEGER` | `NOT NULL`, default `0` |

Available quantity is `quantity_on_hand - quantity_reserved`, computed rather than
stored — a stored third counter is a third thing to get out of sync.

### `stock_reservations`

| Column | Type | Constraints |
|---|---|---|
| `id` | `BIGINT` | PK, identity |
| `public_id` | `VARCHAR(36)` | `NOT NULL`, unique |
| `order_id` | `VARCHAR(36)` | `NOT NULL` — order's public id |
| `sku` | `VARCHAR(64)` | `NOT NULL` |
| `quantity` | `INTEGER` | `NOT NULL`, `CHECK > 0` |
| `status` | `VARCHAR(20)` | `NOT NULL` — `HELD`, `COMMITTED`, `RELEASED`, `EXPIRED` |
| `expires_at` | `TIMESTAMP` | `NOT NULL` |
| `created_at` | `TIMESTAMP` | `NOT NULL` |
| `version` | `INTEGER` | `NOT NULL`, default `0` |

**Indexes:** `ix_reservations_order (order_id)`, `ix_reservations_status_expiry (status, expires_at)`.

`expires_at` plus a scheduled sweep is the backstop for a saga that dies mid-flight.
Without it, one crashed checkout holds stock forever.

### `stock_ledger`

Append-only. Never updated, never deleted.

| Column | Type | Constraints |
|---|---|---|
| `id` | `BIGINT` | PK, identity |
| `sku` | `VARCHAR(64)` | `NOT NULL` |
| `change` | `INTEGER` | `NOT NULL` — signed |
| `reason` | `VARCHAR(30)` | `NOT NULL` — `RECEIPT`, `RESERVE`, `COMMIT`, `RELEASE`, `ADJUSTMENT`, `RETURN` |
| `reference` | `VARCHAR(36)` | nullable — order or reservation id |
| `occurred_at` | `TIMESTAMP` | `NOT NULL` |

The ledger is what lets you answer "why is this SKU at 3?" six weeks later. Current
quantity is derivable from it, which makes it the audit trail *and* a repair
mechanism if the counters ever drift.

### Saga plumbing

| Table | Purpose |
|---|---|
| `outbox` | `id`, `aggregate_id`, `event_type`, `payload`, `created_at`, `published_at` |
| `processed_events` | `event_id` (PK) — idempotency guard |

## API endpoints

This service owns **two** resources, so it gets two paths rather than one
`/api/inventory` namespace — see [URL conventions](../architecture.md). Reservations
are a resource in their own right, not a sub-resource of stock.

### `/api/stock`

| Method | Path | Auth | Purpose |
|---|---|---|---|
| `GET` | `/api/stock/{sku}` | public | Availability for one SKU |
| `POST` | `/api/stock/availability` | public | Batch availability (body: SKU list) — one call for a cart page |
| `GET` | `/api/stock?belowReorderLevel=true` | `ADMIN` | Filter, as a query rather than a `/low-stock` path |
| `PUT` | `/api/stock/{sku}` | `ADMIN` | Set stock level |
| `POST` | `/api/stock/{sku}/adjustments` | `ADMIN` | Signed adjustment with a reason |
| `GET` | `/api/stock/{sku}/ledger` | `ADMIN` | Movement history |

### `/api/reservations`

| Method | Path | Auth | Purpose |
|---|---|---|---|
| `POST` | `/api/reservations` | service | Reserve stock for an order |
| `GET` | `/api/reservations/{id}` | service | Reservation state |
| `DELETE` | `/api/reservations/{id}` | service | Release |
| `POST` | `/api/reservations/{id}/commit` | service | Commit on payment success |

`adjustments` and `ledger` *do* nest under `/api/stock/{sku}` because they are
genuinely about one SKU's stock — a ledger entry has no meaning apart from its SKU.

`GET /api/stock/{sku}` returns availability without exact counts:

```json
{ "sku": "KBD-0001", "available": 12, "inStock": true }
```

Batch availability exists because the alternative is the cart page making one request
per line item.

## Error responses

| Status | Cause |
|---|---|
| `404` | Unknown SKU |
| `409` | Insufficient stock, or optimistic lock conflict after retries exhausted |
| `422` | Reservation requested for an already-committed order |

A `409` on insufficient stock is a normal business outcome, not a defect — the saga
handles it by cancelling the order.

## Events

**Consumes:**

| Event | Action |
|---|---|
| `OrderCreated` | Reserve stock → publish `StockReserved` or `StockRejected` |
| `PaymentAuthorized` | Commit reservation |
| `PaymentFailed` / `OrderCancelled` | Release reservation |

**Publishes** (via outbox):

| Event | When |
|---|---|
| `StockReserved` | Reservation succeeded |
| `StockRejected` | Insufficient stock — cancels the saga |
| `StockCommitted` | Reservation committed |
| `StockReleased` | Reservation released or expired |
| `LowStockDetected` | Crossed reorder level |

Every handler is idempotent via `processed_events`. Kafka redelivers; a
non-idempotent reserve handler would double-reserve on redelivery.

## Decisions deferred

- **Reservation TTL.** Long enough for a slow payment, short enough not to strand
  stock. Needs a real number.
- **Retry policy on optimistic-lock conflict** — attempt count and backoff.
- **Multi-warehouse**, which would add a location dimension to every table. Out of
  scope unless the project grows.
- **Whether availability reads should be cached**, and how stale is acceptable.
- **Backorders** — currently out of stock simply fails the order.
