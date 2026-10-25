# Build guide: inventory-service

A step-by-step plan for implementing Phase 2's hardest service yourself. Each step
ends in a **checkpoint** — something observable. Don't move on until it passes.

Target: [docs/services/inventory-service.md](../services/inventory-service.md) is the
spec. [cart-service](../../services/cart-service) is your closest template — same
shape, same security style, same H2 setup.

**This is the first service where correctness is hard.** Everything so far has been
CRUD behind a security layer. Here two customers can buy the last item at the same
moment, and the system has to be right about it. Steps 4 and 5 are the whole point of
the exercise; the rest is scaffolding around them.

---

## Step 1 — Scaffold the module

Clone the shape of `cart-service`: POM (same dependencies — it also needs
`spring-retry` if you take the annotation route in Step 5), `application.yml` on port
**8083**, `application-dev.yml` pointing at `./data/inventory`,
`application-test.yml` with `mem:inventory-test` and the `jwk-set-uri` override.

Register it in `services/pom.xml` `<modules>`.

**Checkpoint:** `./mvnw -pl services/inventory-service -am install -DskipTests`
succeeds and the module appears in the reactor.

---

## Step 2 — Schema

Write `V1__create_inventory_tables.sql` with three tables. Take the columns from the
spec, but understand *why* each exists:

**`stock_items`** — one row per SKU.

```
quantity_on_hand   INTEGER NOT NULL CHECK >= 0
quantity_reserved  INTEGER NOT NULL CHECK >= 0
version            INTEGER NOT NULL DEFAULT 0
```

**Available is computed, never stored**: `on_hand - reserved`. A third counter is a
third thing that can disagree with the other two.

**`stock_reservations`** — one row per held quantity, with `status` and
`expires_at`.

**`stock_ledger`** — append-only. Never updated, never deleted. Every movement with a
signed `change` and a `reason`.

Two things worth noticing:

- **`sku` is a plain `VARCHAR`, not a foreign key.** Products live in
  catalog-service's database, which this service cannot reach. Same rule as
  `cart_items.product_id`.
- **The `CHECK >= 0` constraints are a safety net, not the mechanism.** They turn a
  logic bug into a failed insert rather than negative stock. The actual correctness
  comes from Step 5.

**Checkpoint:** a context-load test passes — which runs the migration and has
Hibernate validate your entities against it.

---

## Step 3 — Entities and repositories

`StockItem`, `StockReservation`, `StockLedgerEntry`. Put `@Version` on `StockItem`
now, even though nothing uses it yet — adding it later means a migration plus a
backfill.

Repository methods you'll need: `findBySku`, and for the sweep,
`findByStatusAndExpiresAtBefore`.

**Checkpoint:** a repository test saves a stock item, reads it back, and confirms
`version` starts at 0.

---

## Step 4 — Prove the race exists

**Do not skip this.** Write the overselling bug before you fix it.

Write a service method that does the obvious thing:

```java
// Deliberately wrong - this is Step 4's artifact, not Step 5's answer.
public void reserve(String sku, int quantity) {
    StockItem item = stockItems.findBySku(sku).orElseThrow();
    if (item.available() < quantity) {
        throw new InsufficientStockException(sku);
    }
    item.setQuantityReserved(item.getQuantityReserved() + quantity);
    stockItems.save(item);
}
```

Then write a test that hammers it concurrently:

- One SKU with `quantity_on_hand = 1`
- Ten threads, each reserving 1
- An `ExecutorService` plus a `CountDownLatch` so they start together
- Count how many *succeeded*

Three things the test must get right, or it will pass for the wrong reason:

1. **The test method must not be `@Transactional`.** If it is, every thread shares one
   transaction and there is no race to observe.
2. **Each thread needs its own transaction.** Call through the Spring bean so each
   invocation gets one.
3. **A loop is not a race.** Sequential calls will behave correctly. You need actual
   parallelism and a latch to line the threads up.

**Checkpoint:** the test shows **more than one reservation succeeded** against a stock
of 1. You have reproduced overselling. Keep this test — it becomes the regression
guard.

---

## Step 5 — Fix it with optimistic locking

Now make the test pass without changing the test.

`@Version` already causes Hibernate to append `WHERE version = ?` to the update. The
loser of a race gets an `OptimisticLockingFailureException` — so the behaviour you
need is **catch it, re-read, try again**.

### The trap that will cost you an afternoon

**A retry inside the failed transaction cannot work.** Once a transaction has taken
that exception it is marked rollback-only; re-reading inside it fails too. The retry
must wrap the transaction, not sit inside it.

And **self-invocation bypasses the proxy** — exactly the same trap as customer-service's
JIT provisioning. A non-transactional method in the same class calling a
`@Transactional` one does *not* get a transaction.

So the arrangement has to be:

```
StockReservationCoordinator        (no @Transactional, owns the retry loop)
        └── calls ──> StockService.reserve(...)   (@Transactional, a DIFFERENT bean)
```

Write the loop by hand first — three or four attempts with a short backoff — so you
can see the mechanism. Swapping it for Spring Retry's `@Retryable` afterwards is a
two-line change, and you will understand what the annotation is doing.

**Checkpoint:** the Step 4 test now reports **exactly one** success and nine
`InsufficientStock` outcomes, with stock never negative. Run it twenty times — a
concurrency test that passes once has told you very little.

> **Pause here for review.** This is the step most worth a second pair of eyes.

---

## Step 6 — Reservation lifecycle

Reserve is only a third of it. Add:

- **`commit(reservationId)`** — payment succeeded. Move the quantity out of
  `quantity_reserved` and out of `quantity_on_hand`, write a `COMMIT` ledger entry.
- **`release(reservationId)`** — payment failed or the order was cancelled. Return the
  quantity to available, write a `RELEASE` ledger entry.

**Why reservations rather than decrementing at checkout:** a straight decrement
strands stock on every abandoned cart. Reserve-then-commit means an unfinished
checkout gives the stock back.

Every one of these writes a ledger row. The ledger is what makes "why is this SKU at
3?" answerable six weeks later, and it is also a repair mechanism if the counters ever
drift.

**Checkpoint:** reserve → commit leaves `on_hand` reduced and `reserved` back at zero.
Reserve → release leaves both unchanged from where they started. Both leave a ledger
trail you can read.

---

## Step 7 — Expiry sweep

A scheduled job releases reservations past `expires_at`. Model it on
`CartExpirySweeper`: batched, idempotent, `@Scheduled`.

**This is the backstop for a saga that dies mid-flight.** Without it, one crashed
checkout holds stock forever — and in Phase 3 that becomes the difference between a
recoverable failure and permanently unsellable inventory.

**Checkpoint:** a test with a short TTL creates a reservation, waits, runs the sweep,
and sees the stock available again with an `EXPIRED` status and a ledger entry.

> **Pause here for review.**

---

## Step 8 — API

Two resources, per [URL conventions](../architecture.md) — reservations are a resource
in their own right, not a sub-resource of stock:

| Method | Path | Auth |
|---|---|---|
| `GET` | `/api/stock/{sku}` | public |
| `POST` | `/api/stock/availability` | public — batch, so a cart page is one call |
| `GET` | `/api/stock?belowReorderLevel=true` | `ADMIN` |
| `PUT` | `/api/stock/{sku}` | `ADMIN` |
| `POST` | `/api/stock/{sku}/adjustments` | `ADMIN` |
| `GET` | `/api/stock/{sku}/ledger` | `ADMIN` |
| `POST` | `/api/reservations` | service-only |
| `POST` | `/api/reservations/{id}/commit` | service-only |
| `DELETE` | `/api/reservations/{id}` | service-only |

`GET /api/stock/{sku}` returns availability without exact counts — `{ "sku": "…",
"available": 12, "inStock": true }`. Competitors can read your public API.

**A `409` for insufficient stock is a normal business outcome, not a defect.** The
saga handles it by cancelling the order, so don't log it as an error.

**Checkpoint:** the 401/403/200 matrix, as in `CatalogAdminTest`. Public reads work
anonymously; writes need `ADMIN`.

---

## Step 9 — Wire it in

- Gateway route for `/api/stock/**`. **Do not route `/api/reservations/**` publicly** —
  reservations are created by the saga, and exposing them would let a caller reserve
  stock without placing an order.
- `Dockerfile` cloned from cart-service. Watch the `COPY --from=build` path: a
  careless `sed` leaves it pointing at the wrong module's `target/`.
- Compose service on 8083 with a volume at `/app/data`, plus the
  `KEYCLOAK_ISSUER_URI` / `KEYCLOAK_JWK_SET_URI` pair.
- Seed data in `db/seed` so there is stock to reserve in dev.

**Checkpoint:** `docker compose up -d --build`, then reserve stock through the gateway
with a real token.

---

## Definition of done

- [ ] The concurrency test passes twenty times in a row
- [ ] Stock can never go negative, enforced by both `@Version` and a `CHECK`
- [ ] Reserve → commit and reserve → release both leave consistent counters
- [ ] An expired reservation returns its stock automatically
- [ ] Every movement has a ledger row
- [ ] `./mvnw clean install` green from the repo root
- [ ] `docs/services/inventory-service.md` banner changed to ✅, schema matched to the
      migration, and the *Decisions deferred* list updated with what you actually chose

---

## If you get stuck

- **The concurrency test passes immediately, before Step 5** → it isn't concurrent.
  Check the test isn't `@Transactional` and that the latch is doing its job.
- **`OptimisticLockingFailureException` escapes to the client as a 500** → your retry
  is inside the transaction, or it's a self-invocation that never got a proxy.
- **`TransactionRequiredException` on a bulk update** → `@Modifying` query methods get
  no transaction of their own. Same fix as `AddressRepository`.
- **Negative stock in the database** → the `CHECK` constraint is missing. It should
  have made this impossible.
- **Hibernate validation fails at startup** → entity and migration disagree; the
  message names the column.

---

## What this sets up

`order-service` is next, and it calls this service twice per checkout — reserve, then
commit or release. The reservation API you build here *is* the saga's inventory
participant. Getting the lifecycle right now is what makes Phase 3's compensation
logic a matter of calling the right method rather than inventing one.
