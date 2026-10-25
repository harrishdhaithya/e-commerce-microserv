# Documentation

Reference documentation for the e-commerce microservices platform.

## How these documents relate to the others in the repo

| Document | Answers |
|---|---|
| [PLAN.md](../PLAN.md) | **Why and when** — architectural decisions, trade-offs, phased roadmap |
| [README.md](../README.md) | **How to run it** — prerequisites, commands, endpoints |
| `docs/` (here) | **What it is** — architecture reference and per-service schema/API specs |

Some overlap with PLAN.md is deliberate. PLAN.md argues for decisions; these documents
describe the result. When they disagree, the code wins and the document is stale —
please fix it.

## Contents

- **[architecture.md](architecture.md)** — system overview, communication patterns,
  data ownership, cross-cutting concerns, port map
- **[architecture-diagram.md](architecture-diagram.md)** — the system in one picture
- **Build guides** — step-by-step with checkpoints, for implementing a service yourself:
  [phase-1-customer-service](guides/phase-1-customer-service.md) ·
  [phase-2-inventory-service](guides/phase-2-inventory-service.md)

### Service documents

Each contains responsibilities, database schema, API endpoints, and events.

| Service | Port | Status |
|---|---|---|
| [api-gateway](services/api-gateway.md) | 8080 | ✅ **Implemented** |
| [customer-service](services/customer-service.md) | 8081 | ✅ **Implemented** |
| [catalog-service](services/catalog-service.md) | 8082 | ✅ **Implemented** |
| [inventory-service](services/inventory-service.md) | 8083 | ✅ **Implemented** |
| [cart-service](services/cart-service.md) | 8084 | ✅ **Implemented** |
| [order-service](services/order-service.md) | 8085 | 🔵 Planned — Phase 2/3 |
| [payment-service](services/payment-service.md) | 8086 | 🔵 Planned — Phase 2/3 |
| [notification-service](services/notification-service.md) | 8087 | 🔵 Planned — Phase 3 |

## Read this before trusting a document

**Five services exist today** — `api-gateway`, `catalog-service`,
`customer-service`, `cart-service` and `inventory-service`. Their documents describe
code that runs, with schema, routes and endpoints taken from the actual migrations,
configuration and controllers.

The other three are **design specifications**, not descriptions of working software.
Every one carries a banner saying so. Their schemas and endpoints are proposals that
will change once they meet real code — which is normal and fine, as long as nobody
mistakes them for as-built documentation. Each planned document ends with a
*Decisions deferred* section listing what genuinely has not been settled yet, rather
than papering over the gaps.

When you implement one, replace its banner with the implemented one and make the
schema match the migration exactly.
