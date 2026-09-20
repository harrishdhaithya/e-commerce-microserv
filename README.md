# E-Commerce Microservices Platform

A solo learning project: Spring Boot + Angular microservices, built to practise
distributed-system patterns (saga, transactional outbox, event-driven consistency,
distributed tracing) rather than database administration.

- **[PLAN.md](PLAN.md)** — architectural decisions, trade-offs and the phased roadmap
- **[docs/](docs/)** — architecture reference and per-service schema/API documentation
  ([architecture](docs/architecture.md) · [catalog-service](docs/services/catalog-service.md)
  · [customer-service](docs/services/customer-service.md))

**Status: Phases 0 and 1 complete.** `catalog-service` serves the catalog with public
reads and `ADMIN`-only writes, `customer-service` handles profiles and addresses
behind Keycloak, and the Angular SPA browses and signs in with OIDC. The whole stack
runs under `docker compose`. Next is Phase 2: API gateway, cart and the order happy
path.

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

### Everything in Docker (one command)

```bash
cd infra
docker compose up -d --build
```

Starts Keycloak, both services and the SPA. Open **http://localhost:4200** and sign in
as `customer@test.local` or `admin@test.local` (password `password`).

```bash
docker compose ps          # health of each container
docker compose logs -f customer-service
docker compose down        # stop; H2 data and the realm survive in volumes
docker compose down -v     # stop and wipe, forcing a fresh realm import and reseed
```

First build takes a few minutes while Maven and npm populate their caches; later
builds reuse them.

### Or run the services from your IDE

Keeps hot reload and a debugger. Start only the infrastructure in Docker, then run
each service locally — the defaults in `application.yml` already point at
`localhost:8180`, so nothing needs reconfiguring.

**1. Keycloak** — identity provider on port 8180:

```bash
cd infra
docker compose up -d keycloak
```

The realm, both clients and two test users import from
`infra/keycloak/import/ecommerce-realm.json` on first start. Editing that file needs
`docker compose down -v` to take effect — `--import-realm` skips a realm that already
exists.

**2. catalog-service** — port 8082:

```bash
cd services/catalog-service
../../mvnw spring-boot:run
```

**3. customer-service** — port 8081:

```bash
cd services/customer-service
../../mvnw spring-boot:run
```

**4. Frontend** — SPA on port 4200:

```bash
cd frontend
npm install     # first time only
npm start
```

Then open **http://localhost:4200**. Sign in with `customer@test.local` or
`admin@test.local`.

Routing is by path, and there are two copies of it — `frontend/proxy.conf.json` for
the Angular dev server, `frontend/nginx.conf` for the container. Both send
`/api/customers` to 8081 and `/api/products`/`/api/categories` to 8082, so the browser
only ever sees one origin and CORS never comes up. Phase 2 replaces both with a single
gateway target.

### The one Docker subtlety worth knowing

A browser gets its token from `localhost:8180`, so every token's `iss` claim says
`localhost:8180`. But inside a container, `localhost` is *that container* — so a
containerised service cannot fetch Keycloak's signing keys from there. Hence the two
settings in `docker-compose.yml`:

```yaml
KEYCLOAK_ISSUER_URI:  http://localhost:8180/realms/ecommerce          # validate iss
KEYCLOAK_JWK_SET_URI: http://keycloak:8080/realms/.../certs           # fetch keys
```

Validate the issuer as the browser sees it; fetch the keys as the container sees it.
Collapsing these into one `issuer-uri` is the classic route to an opaque
`401 invalid_token`.

### Getting a token without a browser

Handy for `curl`-ing the API directly:

```bash
curl -s -X POST http://localhost:8180/realms/ecommerce/protocol/openid-connect/token \
  -d grant_type=password -d client_id=ecom-dev-cli \
  -d username=customer@test.local -d password=password
```

`ecom-dev-cli` exists solely for this. It is a dev-only client — direct access grants
should never be enabled on anything you ship.

### Useful endpoints

| URL | What |
|---|---|
| http://localhost:4200 | The store |
| http://localhost:8180 | Keycloak admin console (`admin` / `admin`) |
| http://localhost:8082/swagger-ui.html | catalog-service API docs |
| http://localhost:8081/swagger-ui.html | customer-service API docs |
| http://localhost:8082/h2-console | catalog database console (dev only) |
| http://localhost:8081/h2-console | customer database console (dev only) |
| http://localhost:808{1,2}/actuator/health | Health checks |

H2 console login: JDBC URL `jdbc:h2:file:./data/catalog` or `./data/customer`, user
`sa`, blank password. The dev datasource sets `AUTO_SERVER=TRUE`, so you can attach
while the service runs.

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
