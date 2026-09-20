# E-Commerce Microservices Platform

A solo learning project: Spring Boot + Angular microservices, built to practise
distributed-system patterns (saga, transactional outbox, event-driven consistency,
distributed tracing) rather than database administration.

- **[PLAN.md](PLAN.md)** — architectural decisions, trade-offs and the phased roadmap
- **[docs/](docs/)** — architecture reference and per-service schema/API documentation
  ([architecture](docs/architecture.md) · [catalog-service](docs/services/catalog-service.md))

**Status: Phase 0 complete.** `catalog-service` serves a real catalog and the Angular
SPA browses it. No Docker required yet — H2 is embedded.

## Stack

| Layer | Choice |
|---|---|
| Backend | Spring Boot 3.5.0, Java 21, Maven multi-module |
| Database | H2, embedded, one per service (file in dev, in-memory in tests) |
| Migrations | Flyway, `ddl-auto: validate` |
| Frontend | Angular 21, standalone components, signals, zoneless |
| API docs | springdoc-openapi (Swagger UI) |

## Prerequisites

- JDK 21+ (built with `release=21`; a JDK 25 toolchain works)
- Node 20.19+, 22.12+, or 24+ (Angular 21's floor)
- No database to install

## Running it

Two terminals.

**Backend** — `catalog-service` on port 8082:

```bash
cd services/catalog-service
../../mvnw spring-boot:run
```

**Frontend** — SPA on port 4200:

```bash
cd frontend
npm install     # first time only
npm start
```

Then open **http://localhost:4200**.

The Angular dev server proxies `/api` to port 8082 (`frontend/proxy.conf.json`), so the
browser only ever sees one origin and CORS never comes up. From Phase 2 the same
relative path points at the API gateway instead, with no frontend changes.

### Useful endpoints

| URL | What |
|---|---|
| http://localhost:4200 | The store |
| http://localhost:8082/swagger-ui.html | Interactive API docs |
| http://localhost:8082/v3/api-docs | OpenAPI JSON |
| http://localhost:8082/actuator/health | Health check |
| http://localhost:8082/h2-console | Database console (dev only) |

H2 console login: JDBC URL `jdbc:h2:file:./data/catalog`, user `sa`, blank password.
The dev datasource sets `AUTO_SERVER=TRUE`, so you can attach while the service runs.

## Tests

```bash
./mvnw test                          # backend: 10 tests, ~4s
cd frontend && npm test -- --watch=false   # frontend: 8 tests, ~1s
```

Backend integration tests run Flyway against in-memory H2 and then have Hibernate
validate the entity mappings against the resulting schema — a typo in a column name
fails the suite rather than surfacing at runtime.

## Layout

```
pom.xml                  Root aggregator: version management, module list, no code
common/
  common-web/            Shared ApiError, exception handler, correlation-ID filter.
                         Self-wiring via Spring auto-configuration.
  common-events/         Cross-service event contracts. Event DTOs only.
services/
  catalog-service/       Products, categories, search (port 8082)
frontend/                Angular workspace
data/                    H2 files, git-ignored
```

## Conventions worth keeping

- **A service touches only its own database.** Need another service's data? Call its
  API or consume its events.
- **Flyway owns the schema.** `ddl-auto: validate`, never `update`.
- **Vendor-neutral SQL** in migrations, dialect never hardcoded in Java — so swapping
  H2 for PostgreSQL or Oracle stays a one-day job. See PLAN.md §4.
- **Money is `BigDecimal`** on `NUMERIC(19,4)`. Time is UTC.
- **Internal IDs stay internal.** Anything in a URL or crossing a service boundary
  uses the `public_id` UUID.
- **Seed data lives in `db/seed`**, loaded only in the dev profile, so no test can
  accidentally pass on demo rows.

## Documentation

| Document | Contents |
|---|---|
| [docs/architecture.md](docs/architecture.md) | System overview, communication patterns, data ownership, port map |
| [docs/services/](docs/services/) | One document per service: responsibilities, schema, API endpoints, events |
| [PLAN.md](PLAN.md) | Why each decision was made, and the phase it lands in |

Only `catalog-service` is implemented; the other service documents are design
specifications and say so at the top.

## What's next

Phase 1: Keycloak as the identity provider, `customer-service` for profiles and
addresses, and catalog writes behind an `ADMIN` role. See PLAN.md §8 and
[docs/services/customer-service.md](docs/services/customer-service.md).
