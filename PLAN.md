# E-Commerce Microservices Platform — Build Plan

**Stack:** Spring Boot (Java 21) · Angular · H2 (embedded, one per service) · Kafka · Docker

## 0. Assumptions

These shape the plan. Change any of them and tell me — several sections move.

- **Purpose:** a substantial portfolio/academic project that is production-*shaped*, not a system serving real payment traffic. Real money and PCI compliance are out of scope; payment is a simulated gateway.
- **Team size:** solo, learning-focused. This is why the plan favours fewer/fatter services, a monorepo, and the least infrastructure that still teaches the real lessons.
- **Deployment target:** Docker Compose locally, with Kubernetes as an optional stretch phase. Not committed to a cloud provider.
- **Database:** **H2, one embedded database per service** — file-mode for development, in-memory for tests. Each service embeds its own DB in its own JVM, which is actually *stricter* isolation than the Oracle schema-per-service setup: cross-service joins aren't merely forbidden, they're impossible. §4 covers how to keep a later swap to a real database cheap.
- **Scale:** correctness and clean boundaries matter; sharding, multi-region, and read replicas do not.

## 1. Architecture at a glance

```
      ┌──────────┐  login (OIDC + PKCE, browser redirect)
      │ Keycloak │ ◀───────────────┐
      └────┬─────┘                 │              Keycloak is NOT in the request
           │ JWKS (public keys)    │              path — services validate tokens
           ▼                       │              offline with cached public keys
                        ┌──────────┴───┐
    Angular SPA ──────▶ │ API Gateway  │ ── JWT validation, routing, rate limit
                        └──────┬───────┘
                               │ REST + bearer token (sync, read paths)
        ┌───────────┬──────────┼───────────┬────────────┬───────────┐
        ▼           ▼          ▼           ▼            ▼           ▼
    customer    catalog      cart       order        payment    inventory
        │           │          │           │            │           │
      [ H2 ]     [ H2 ]     [ H2 ]      [ H2 ]       [ H2 ]      [ H2 ]   ← one embedded DB each
        │           │          │           │            │           │
        └───────────┴──── Kafka (async, write paths) ───┴────────────┴─────┐
                                                                           ▼
                                                                     notification
```

**Two rules that keep this from becoming a distributed monolith:**

1. A service touches **only its own database**. No shared tables, no cross-service joins. With embedded H2 this is free — each service's data lives inside its own process and is physically unreachable from the others. If a service needs another's data, it calls its API or listens to its events.
2. **Sync for reads, async for writes with side effects.** Browsing a product = REST call. Placing an order = event chain. This is the single most important design decision in the project.

## 2. Service catalog

| Service | Owns | Key tables | Port |
|---|---|---|---|
| `api-gateway` | routing, JWT check, CORS, rate limiting | — | 8080 |
| `customer-service` | customer profile + addresses (**no** credentials, roles, or tokens — Keycloak owns those) | `CUSTOMERS`, `ADDRESSES` | 8081 |
| `catalog-service` | products, categories, brands, media, reviews | `PRODUCTS`, `CATEGORIES`, `PRODUCT_VARIANTS`, `PRODUCT_IMAGES`, `REVIEWS` | 8082 |
| `inventory-service` | stock levels, reservations | `STOCK_ITEMS`, `STOCK_RESERVATIONS`, `STOCK_LEDGER` | 8083 |
| `cart-service` | active carts | `CARTS`, `CART_ITEMS` | 8084 |
| `order-service` | orders, order lines, order state machine, saga orchestration | `ORDERS`, `ORDER_ITEMS`, `ORDER_EVENTS`, `OUTBOX` | 8085 |
| `payment-service` | payment intents, transactions, refunds | `PAYMENTS`, `PAYMENT_ATTEMPTS`, `REFUNDS`, `OUTBOX` | 8086 |
| `notification-service` | email/SMS templates and delivery log | `NOTIFICATIONS`, `TEMPLATES` | 8087 |

**Deliberately *not* separate services:** search (plain SQL `LIKE`/predicate filtering inside `catalog-service`; extract to Elasticsearch only if you actually need it), shipping (a module in `order-service`), pricing/promotions (start inside `catalog-service`), review moderation. Splitting these early buys you eight more deployment units and zero learning.

**Also deliberately dropped for now:** Redis. Carts live in the cart service's own H2 tables with a scheduled sweep for expiry. Add Redis in Phase 3 *if* you want to learn cache/TTL semantics — it's a clean, isolated upgrade, and doing it later means you'll have a working system to compare against.

**Why `order-service` orchestrates:** order placement is the one flow where you need a visible, debuggable state machine. Orchestration (order-service tells others what to do) is far easier to reason about and demo than pure choreography, and it gives you one place to look when something is stuck.

## 3. The order-placement saga

This is the centrepiece of the project — build it deliberately.

```
POST /orders
  ├─ order-service: create ORDER (status=PENDING) + OUTBOX row, single local tx
  ├─ event OrderCreated
  ├─ inventory-service: reserve stock
  │     ├─ ok  → StockReserved
  │     └─ fail → StockRejected ──────────────┐
  ├─ payment-service: authorize payment       │
  │     ├─ ok  → PaymentAuthorized            │
  │     └─ fail → PaymentFailed ──────┐       │
  ├─ order-service: status=CONFIRMED  │       │
  ├─ inventory: commit reservation    │       │
  └─ notification: order confirmation │       │
                                      ▼       ▼
                        COMPENSATE: release reservation,
                        void authorization, status=CANCELLED,
                        notify customer
```

One modelling detail while you're here: `order-service` **copies** the shipping address into `ORDERS` at checkout rather than storing a reference to `customer-service`. An order is a record of what was agreed at a point in time — editing your address next month must not rewrite last month's orders. This is also what lets `order-service` render order history without calling `customer-service` at all.

Implementation requirements, in priority order:

1. **Transactional outbox.** Never write to the database and publish to Kafka in the same logical step hoping both succeed. Write an `OUTBOX` row in the same transaction as the business change; a scheduled poller publishes and marks it sent. This is ~80 lines of code and removes the most common class of bug in this kind of system.
2. **Idempotent consumers.** Every handler keys off an event ID stored in a `PROCESSED_EVENTS` table with a unique constraint. Kafka redelivers; assume it will.
3. **Explicit state machine.** `ORDERS.STATUS` transitions are validated in code (`PENDING → STOCK_RESERVED → PAID → CONFIRMED → SHIPPED → DELIVERED`, plus `CANCELLED`/`FAILED`). Log every transition to `ORDER_EVENTS` — this table is your demo and your debugger.
4. **Saga timeout.** A scheduled job cancels orders stuck in a non-terminal state past N minutes. Without this, one dropped message leaves a permanently hung order.

## 4. H2 setup — and keeping the swap cheap

### Configuration

**Dev profile — file mode, not in-memory.** Pure in-memory means your seeded catalog, test users, and any half-finished order state vanish on every restart. You will restart dozens of times a day. Use file mode for dev and reserve in-memory for tests:

```yaml
# application-dev.yml  (each service, its own filename)
spring:
  datasource:
    url: jdbc:h2:file:./data/catalog;AUTO_SERVER=TRUE;DB_CLOSE_DELAY=-1
    username: sa
    password: ""
  jpa:
    hibernate.ddl-auto: validate      # Flyway owns the schema. Never 'update'.
    open-in-view: false
  h2.console:
    enabled: true                     # dev profile ONLY
    path: /h2-console
  flyway:
    enabled: true
```

```yaml
# application-test.yml
spring:
  datasource:
    url: jdbc:h2:mem:test;DB_CLOSE_DELAY=-1
```

- `AUTO_SERVER=TRUE` lets you attach a SQL client to a running service's database — the closest thing you get to inspecting live state, and worth having from day one.
- The **H2 console** per service (at `localhost:808x/h2-console`) is genuinely useful for a distributed system: you can see each service's data separately, which makes the ownership boundaries concrete.
- **`ddl-auto: validate`, with Flyway owning the schema.** The tempting shortcut here is `ddl-auto: update` with no migrations, since H2 is disposable anyway. Don't take it. Writing migrations is a skill the project is meant to teach, and `validate` catches entity/schema drift immediately instead of silently reshaping your tables.

### Conventions that keep a future database swap cheap

You may well want Oracle or Postgres later — for the CV line, or because you hit something H2 can't model. These habits make that a one-day job instead of a rewrite:

- **Write vendor-neutral SQL in migrations.** Plain `VARCHAR`, `NUMERIC(19,4)`, `TIMESTAMP`, standard constraint syntax. No H2-specific functions.
- **Keys:** `GenerationType.IDENTITY` for internal PKs, and a separate `UUID` public-ID column for anything that appears in a URL or crosses a service boundary. Never leak sequential internal IDs between services — that coupling is the expensive kind to undo.
- **Money is always `BigDecimal`** mapped to `NUMERIC(19,4)`. Never `double`, not even for a subtotal you "only display".
- **Time is always UTC**, `TIMESTAMP` → `OffsetDateTime` or `Instant`. Decide once, now.
- **Don't name the vendor in Java.** No dialect hardcoded in code, no vendor-specific `@Query`. Dialect stays in config, where a profile can override it.
- **Keep the driver a runtime dependency** behind a profile, so adding a second one later doesn't touch application code.
- **Optional:** you *can* run H2 in Oracle compatibility mode (`;MODE=Oracle`). I'd skip it — it emulates enough to give you false confidence but not enough to catch real Oracle problems. Portable SQL is the better discipline.

### What H2 costs you (know these, they're small)

- **Concurrency semantics differ.** H2's locking is not Oracle's or Postgres's. For inventory decrements, use an optimistic `@Version` column with retry rather than `SELECT … FOR UPDATE` — version-based optimistic locking is JPA-level and behaves consistently across every database, so it's both the portable choice and the one worth learning.
- **No real query plans.** Performance tuning, index strategy, and `EXPLAIN` work aren't learnable here. That's fine — they're a separate topic from microservices, and this project is about distribution.
- **No full-text search.** Category/attribute filtering plus `LOWER(name) LIKE ?` covers a demo catalog comfortably.

### Testing — the real win

Dropping the Oracle container removes Testcontainers entirely from the DB layer. Integration tests spin up an in-memory H2 in milliseconds, so `@SpringBootTest` with a fresh database per test class is cheap. Use `@Transactional` rollback or an explicit truncate between tests. Aim to keep the whole suite under 30 seconds; this speed is worth real money in a solo project because it's what keeps you actually running the tests.

You'll still want Testcontainers for **Kafka** in Phase 3 — that one's worth it.

## 5. Cross-cutting concerns

| Concern | Choice | Note |
|---|---|---|
| Auth | Keycloak (Docker) — see §5.1 | Rolling your own JWT auth teaches you more but costs a week and will have holes |
| Gateway | Spring Cloud Gateway | Validates JWT once, forwards claims; services re-validate signature but skip user lookup |
| Service discovery | Docker Compose DNS → Kubernetes Services | Skip Eureka unless you specifically want to learn it; container DNS is enough and is what you'd use in production on k8s |
| Config | Per-service `application.yml` + env vars + `.env` | Spring Cloud Config Server is an optional later addition; it's an extra moving part with little payoff at this size |
| Messaging | Kafka (Redpanda locally for a lighter footprint) | RabbitMQ is simpler, Kafka is more employable and gives you replay |
| Resilience | Resilience4j: circuit breaker + timeout + retry on every outbound call | Every `RestClient` call gets a timeout. No exceptions. |
| Observability | Actuator, Micrometer → Prometheus, Grafana, OpenTelemetry → Jaeger/Zipkin | Distributed tracing is the thing that makes a microservices demo impressive *and* debuggable. Do it in Phase 3, not Phase 8. |
| Logging | JSON structured logs with trace/span ID + correlation ID | Grep-across-six-services is painful without it |
| API contracts | OpenAPI per service via springdoc; generate Angular clients from the specs | Kills a whole category of frontend/backend drift |

## 5.1 Keycloak as the identity provider

**Keycloak owns authentication and authorization entirely.** No service stores a password hash, issues a token, or has a `USERS`/`ROLES`/`REFRESH_TOKENS` table. What used to be `identity-service` is now `customer-service`, and it holds only domain data.

### The split, and why it isn't redundant

| Keycloak owns | `customer-service` owns |
|---|---|
| credentials, password policy, reset flows | display name, phone, preferences, marketing opt-in |
| tokens: issue, refresh, revoke, expiry | shipping/billing addresses (a real one-to-many) |
| roles and group membership | loyalty tier, order-history preferences |
| login UI, MFA, social login, account console | anything another service needs to query or join on |
| sessions and logout | |

The tempting move is to delete `customer-service` and keep everything in Keycloak user attributes. Don't — that's the one trap in this design. Keycloak attributes are flat key-value strings: a customer with three addresses becomes `address_1_line1`, `address_2_line1`, and so on. You can't query them ("all customers in this city"), can't validate them properly, can't put a foreign key on them, and every read means an authenticated call to Keycloak's Admin REST API, which makes your IdP a hard runtime dependency of your checkout flow. Keeping your domain's customer record separate from the IdP's identity record is the standard production pattern, and it's worth learning as one.

`customer-service` is small — two tables, CRUD, one JIT-provisioning path. That's fine. It exists to hold the boundary.

### Wiring

**Realm and clients** — one realm `ecommerce`, with:
- `ecom-web` — public client, authorization-code + PKCE, for the Angular SPA. No client secret (a SPA cannot keep one).
- `ecom-gateway` / services — resource servers. They only validate tokens; no client credentials needed for the basic flow.

**Token validation.** Every service is a Spring resource server:

```yaml
spring.security.oauth2.resourceserver.jwt:
  issuer-uri: http://localhost:8180/realms/ecommerce
```

That's the whole backend auth config. Spring fetches the JWKS once, caches the public keys, and validates signature/issuer/audience/expiry **offline**. Keycloak being down does not break authenticated requests to your API — only new logins. Worth internalizing: it's why JWT is used this way.

**Gateway vs. services.** The gateway validates the token and rejects garbage early; each service validates independently too. That's not redundant — a service must never trust a caller just because it's inside the network.

**Roles.** Realm roles `CUSTOMER` and `ADMIN`, landing in the token under `realm_access.roles`. Spring doesn't read that shape by default, so write one small `JwtAuthenticationConverter` that maps those to `ROLE_`-prefixed authorities — put it in `common-web` so all services share it. Then `@PreAuthorize("hasRole('ADMIN')")` works normally.

**The `sub` claim is your customer key.** Keycloak's `sub` (a UUID) is the stable user identifier. `CUSTOMERS.KEYCLOAK_ID` is a unique, non-null column holding it. Never key customer data on email — people change emails, and Keycloak lets them.

**JIT provisioning.** On the first authenticated request from an unknown `sub`, create the customer row from the token's claims (`sub`, `email`, `given_name`, `family_name`). Simpler and more robust than webhooks or Keycloak SPI event listeners, and it self-heals if a row is ever missing.

**Don't put domain data in the token.** No addresses, no loyalty tier, no cart ID. Tokens are cached by the client and can't be invalidated mid-life; stale authorization data is a genuine security bug. Keep claims to identity and roles, and look the rest up.

### Local development

- Keycloak in Compose with `start-dev` (which uses its own embedded H2 — no database to provision, fitting the §4 approach).
- **Export the realm to JSON and commit it**, loaded via `--import-realm` on startup. This is the difference between a project that starts with one command and one that needs twenty minutes of admin-console clicking after every `docker compose down -v`. Do it the first day you touch Keycloak.
- Seed two users in that export: `customer@test.local` and `admin@test.local`, known passwords. They belong in version control — this realm is local-dev only.
- Port 8180 to stay clear of the gateway on 8080.

### Testing

Do **not** start Keycloak for tests. Use `spring-security-test`:

```java
mockMvc.perform(get("/api/customers/me")
    .with(jwt().jwt(j -> j.claim("sub", "test-user-id"))
               .authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"))));
```

This keeps the fast test suite from §4 fast. Cover the real Keycloak integration once in the Phase 5 Playwright E2E, where an actual browser login is the point.

### What you give up

You won't learn to implement password hashing, token issuance, or refresh-rotation yourself. That's a real loss, but it's a different project — and hand-rolled auth in a portfolio piece reads as a liability to anyone who knows what to look for, whereas correct OIDC integration reads as competence.

## 6. Angular frontend

**Structure** (standalone components, signals for state, lazy-loaded feature routes):

```
src/app/
  core/           auth (guards, interceptors, token refresh), http, error handling
  shared/         ui components, pipes, directives
  features/
    catalog/      product list, filters, detail
    cart/         cart drawer, line-item editing
    checkout/     address → payment → review → confirm (stepper)
    orders/       history, order detail with live status
    account/      profile, addresses
    admin/        product CRUD, stock, order management (separate lazy route + role guard)
  generated/      OpenAPI-generated API clients (git-ignored, built in CI)
```

Decisions worth making up front:

- **Talk to the gateway, not to services.** The SPA knows one base URL.
- **Auth:** OIDC authorization-code + PKCE against Keycloak (`angular-auth-oidc-client`). Access token in memory, refresh handled by the library. Not localStorage.
- **State:** signals + a small store service per feature. Reach for NgRx only if the state genuinely becomes cross-cutting and complex — for this app it probably won't.
- **Order status page** should subscribe to updates (SSE from `order-service` through the gateway) so the saga is visible in the UI as it progresses. This is the single best demo feature in the project.
- **Handle the async reality in the UX:** checkout returns "order received, processing" and then resolves. Don't build UI that assumes a synchronous success/fail.

## 7. Repository and build layout

Single monorepo. With 1–3 developers, cross-service refactors in eight repos will cost you more than the coupling risk.

```
e-commerce-microserv/
  pom.xml                    # parent: dependencyManagement, Spring Boot + Spring Cloud BOMs
  common/
    common-events/           # shared event DTOs + versioning (the ONLY shared code, keep it tiny)
    common-web/              # error model, correlation-ID filter, base exception handling
  services/
    api-gateway/  customer-service/  catalog-service/  inventory-service/
    cart-service/ order-service/     payment-service/  notification-service/
  frontend/                  # Angular workspace
  infra/
    docker-compose.yml       # kafka, keycloak, prometheus, grafana, jaeger  (no database!)
    docker-compose.dev.yml   # infra only, for running services from the IDE
    k8s/                     # optional phase 8
  data/                      # H2 files, git-ignored
  docs/
    architecture.md          # system reference: patterns, data ownership, port map
    services/                # one doc per service: schema, endpoints, events
    adr/                     # architecture decision records — write one per major choice
```

A note on `common-events`: shared libraries are how microservices quietly re-couple. Allow event DTOs and nothing else. Anything tempting you to put business logic there belongs in a service.

## 8. Phased roadmap

Estimates assume part-time work by one person. Halve them for a focused full-timer.

**Phase 0 — Foundations** — ✅ **complete**
Monorepo aggregator POMs, `common-web` (shared `ApiError`, exception handler, correlation-ID filter, self-wiring via auto-configuration), `common-events` (the `DomainEvent` contract for Phase 3), `catalog-service` end to end (entities → Flyway migration → repositories → REST → OpenAPI → 10 tests), and an Angular 21 SPA browsing it (debounced search, category filter, sort, pagination, skeleton/empty/error states, 8 tests). **No Docker** — H2 is embedded, so it's `mvn spring-boot:run` and `npm start`.
*Exit criterion met:* two commands, then browse products at localhost:4200. Verified end to end in a browser, including killing the backend mid-session and recovering via "Try again".
See [README.md](README.md) to run it. Everything else clones this skeleton.

**Phase 1 — Catalog and identity** — ✅ **complete**
Keycloak in Docker per §5.1, realm JSON committed and imported, `ecom-web` PKCE client plus a dev CLI client, two seed users. `customer-service`: profile, addresses, JIT provisioning off the `sub` claim, role-based admin endpoints. `catalog-service` is now a resource server — public reads, `ADMIN`-only product and category CRUD. Angular: OIDC login with PKCE, route guard, account page. The whole stack also runs under `docker compose`.
*Deferred by choice:* product variants, multiple images and brands — the catalog model stays simple until something needs them. No admin UI yet; the write endpoints are exercised by tests and `curl`.
*Exit criterion:* log in through Keycloak's own login page, land back in the SPA authenticated, and edit an address that persists. Then stop Keycloak and confirm authenticated API calls still work — that's the offline-JWT-validation lesson in one experiment.

**Phase 2 — Gateway, cart, and order happy path (2 weeks)** — 🟡 gateway + cart done
Spring Cloud Gateway with JWT validation and routing; the SPA now goes through it only. ✅ *Gateway built: Spring Cloud 2025.0.3, reactive, edge authentication with role checks left on the services. Both the dev proxy and nginx collapsed from three routes to one.* `cart-service` with its own H2 tables. ✅ *Built: anonymous carts keyed by an `X-Cart-Token` header, merge-on-login, price snapshots re-validated against catalog-service, and a scheduled expiry sweep standing in for the Redis TTL that was dropped. Also the project's first service-to-service call — with a timeout, and an outage distinguished from a missing product.* `order-service` creating orders via **synchronous** calls to inventory and payment (no Kafka yet). `inventory-service` and a stub `payment-service`.
*Exit criterion:* end-to-end checkout works. It's fragile and synchronous — that's the point. Feeling that fragility is what makes Phase 3 make sense.

**Phase 3 — Events, saga, and observability (2–3 weeks) ← the important phase**
Kafka in Compose. Convert order placement to the outbox + saga design in §3, with compensation and timeouts. Idempotent consumers. `notification-service` consuming events. Resilience4j on every remaining sync call. OpenTelemetry tracing across all services into Jaeger; Prometheus + Grafana dashboards. Angular: live order status via SSE.
*Exit criterion:* kill `payment-service` mid-checkout and the order cancels cleanly with stock released — and you can show the whole thing as one trace in Jaeger.

**Phase 4 — Admin and operations (1–2 weeks)**
Admin UI: product CRUD, stock adjustment, order search and manual state override, saga-failure inspection. Role-based authorization enforced at gateway *and* service, driven by Keycloak realm roles (§5.1). Audit logging keyed on the `sub` claim.

**Phase 5 — Hardening (1–2 weeks)**
Contract tests (Spring Cloud Contract or Pact) between order↔payment↔inventory. Playwright E2E over the critical paths. Load test with k6 — with H2 you're measuring your own service code and the saga rather than the database, which is arguably the more interesting result. Input validation, rate limiting, security headers, dependency scan. Graceful shutdown and readiness/liveness probes.

**Phase 6 — CI/CD (1 week)**
GitHub Actions: per-service build/test with path filters, Jib or buildpacks for images, migration check, frontend build, image push. Nothing deploys without green tests.

**Phase 7 — Documentation (a few days, do it incrementally)**
README with a one-command start, architecture diagram, ADRs, per-service README, seeded demo data, and a scripted demo walkthrough. For a portfolio project this phase is worth more than any additional feature.

**Phase 8 — Optional: Kubernetes** Manifests/Helm, HPA, ingress. Note that embedded H2 and multiple replicas are incompatible (each pod would have its own private data), so this is the phase where you'd swap to a real database per service — which is exactly what the §4 conventions are for. Do this only after everything above is solid.

## 9. Versions — as actually pinned

Settled during Phase 0 and verified by a green build:

| What | Version | Where |
|---|---|---|
| Spring Boot | **3.5.0** | root `pom.xml` parent |
| Java | **21** (toolchain has JDK 25; compiled with `release=21`) | `java.version` property |
| Maven | 3.9.12, via committed wrapper | `.mvn/wrapper/maven-wrapper.properties` |
| springdoc-openapi | 2.8.6 — not in the Boot BOM, so pinned by hand | root `<properties>` |
| H2, Flyway, Hibernate | whatever the Boot BOM manages — never pin these yourself | inherited |

Boot **3.5.0 over 4.1.1** was a deliberate choice: on a solo learning project the tutorials, Baeldung articles and StackOverflow answers you'll lean on overwhelmingly target Boot 3, and translating every one of them is a tax with no learning in it. Bump to the newest 3.5 patch with:

```
curl -s https://repo1.maven.org/maven2/org/springframework/boot/spring-boot-starter-parent/maven-metadata.xml | grep -o '<version>3\.5\.[0-9]*</version>' | tail -3
```

**Angular 21.2** (not 22) because the local Node is 24.13.0 and Angular 22 requires `^22.22.3 || ^24.15.0 || >=26`, while 21 accepts `>=24.0.0`. To move up later: `nvm install 24` (nvm is installed, but the active Node is the `/usr/local/bin` pkg install, so `nvm use` it), then `ng update @angular/core @angular/cli`. The app is **zoneless** — no `zone.js` dependency, which is Angular 21's default and the reason component state is signals throughout.

Still to verify when you reach it:
- **Spring Cloud BOM** for Phase 2's gateway — take the train matching Boot 3.5 from the compatibility matrix. Mismatched Boot/Cloud versions are the #1 startup failure on projects like this.

Verified along the way:
- Flyway 10 is modularized, but **H2 support ships inside `flyway-core`** — no `flyway-database-h2` artifact needed.
- springdoc 2.8.6 works with Boot 3.5.0. (Boot 4.x would need springdoc 3.x.)

## 10. Risks, and what to do about them

| Risk | Mitigation |
|---|---|
| H2 hides database-specific problems (locking, plans, vendor SQL) | Accepted trade-off — those are a separate topic. The §4 conventions keep the swap cheap if you ever want to learn them on a real database. |
| H2's disposability tempts you to skip Flyway | `ddl-auto: validate` from day one makes migrations the only way the schema can change |
| Losing dev data on every restart kills momentum | File mode, not in-memory; plus a seed-data migration so a wipe costs you nothing |
| Too many services for one solo developer to finish | The catalog in §2 is already the trimmed version. If time is short, drop `notification-service` (log instead of send) and fold `inventory` into `order`. Don't add services. |
| Distributed transactions done wrong | §3 is non-negotiable: outbox, idempotency, explicit states, timeouts. No 2PC, no XA. |
| Local dev becomes unbearable (8 services to start) | `docker-compose.dev.yml` runs infra only; run the 2–3 services you're working on from the IDE. Make this work in Phase 0. |
| Scope creep into recommendations/promotions/wishlists | Keep a `docs/backlog.md`. Nothing enters until Phase 5 is done. |
| Debugging across services | Tracing in Phase 3, not later. The cost of adding it early is one day; the cost of not having it is every day after. |

## 11. First three concrete tasks

1. Parent `pom.xml` with the Spring Boot and Spring Cloud BOMs, then `catalog-service` from `start.spring.io` (Web, Data JPA, Validation, Flyway, Actuator, **H2**). Configure it per §4: file-mode H2, `ddl-auto: validate`, console on in dev.
2. `V1__create_products.sql` + a `Product` entity + `ProductRepository` + `GET /api/products` with pagination, and one `@SpringBootTest` proving it works end to end against in-memory H2.
3. `frontend/` Angular workspace with a product-list component reading from `catalog-service`, CORS configured.

Done well, those three give you the vertical slice everything else clones — and with H2 embedded you should reach a browsable product list on day one.
