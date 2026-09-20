# api-gateway

> 🔵 **Planned — Phase 2. Not implemented.** This is a design specification. Nothing
> described here exists yet, and details will change on contact with real code.

Single entry point for the SPA. Routes requests to services, validates JWTs at the
edge, and applies cross-cutting policy (CORS, rate limiting, request logging).

| | |
|---|---|
| **Port** | 8080 |
| **Database** | **None.** The gateway is stateless by design |
| **Module** | `services/api-gateway` (planned) |
| **Technology** | Spring Cloud Gateway |

## Responsibilities

**Owns:** routing, edge JWT validation, CORS policy, rate limiting, correlation-ID
generation for requests arriving without one.

**Does not own:** business logic of any kind, and no data. A gateway that starts
aggregating or transforming domain data becomes a distributed monolith's front door.

The value it adds: the SPA knows exactly one base URL, and garbage tokens are
rejected before they cost a service a thread.

## Routing table

Routes are keyed on **resource** paths, not service names — see
[URL conventions](../architecture.md). A service owning two resources therefore gets
two routes, which is the price of keeping service topology out of the public API.

| Path | Target | Auth |
|---|---|---|
| `/api/products/**` | catalog-service:8082 | Public for `GET`; `ADMIN` otherwise |
| `/api/categories/**` | catalog-service:8082 | Public for `GET`; `ADMIN` otherwise |
| `/api/customers/**` | customer-service:8081 | `CUSTOMER` |
| `/api/cart/**` | cart-service:8084 | `CUSTOMER` |
| `/api/stock/**` | inventory-service:8083 | Public for single-SKU `GET`; `ADMIN` otherwise |
| `/api/reservations/**` | inventory-service:8083 | Internal — not exposed to the SPA |
| `/api/orders/**` | order-service:8085 | `CUSTOMER`; `ADMIN` for cross-customer queries |
| `/api/payments/**` | payment-service:8086 | `CUSTOMER` |
| `/api/notifications/**` | notification-service:8087 | `ADMIN` |
| `/actuator/**` | gateway's own | Restricted |

Targets resolve by container DNS rather than a discovery server — see
[architecture.md](../architecture.md).

**`/api/reservations/**` should not be routed publicly at all.** Reservations are
created by the saga, not by clients; exposing them would let a caller reserve stock
without placing an order. Either omit the route or restrict it to internal callers.

## Security posture

Configuration is the same one property every service uses:

```yaml
spring.security.oauth2.resourceserver.jwt:
  issuer-uri: http://localhost:8180/realms/ecommerce
```

**The gateway validates, and every service validates again.** Not redundant: a
service must never trust a caller merely because the call arrived from inside the
network. The gateway's job is rejecting obvious garbage early, not becoming the only
line of defence.

Validation is offline against Keycloak's cached JWKS, so Keycloak being down does not
break authenticated traffic.

## Filters

| Filter | Behaviour |
|---|---|
| Correlation ID | Generate `X-Correlation-Id` if absent; propagate downstream |
| JWT validation | Reject malformed/expired/wrong-issuer tokens with `401` |
| Rate limiting | Per-IP for anonymous, per-`sub` for authenticated |
| Circuit breaker | Resilience4j per route, with a fallback response |
| Request logging | Method, path, status, duration, correlation ID |

## Error responses

The gateway returns the same `ApiError` shape as every service, so the SPA needs one
error handler. Gateway-originated failures carry no `correlationId` when rejection
happens before the filter runs.

| Status | Cause |
|---|---|
| `401` | Missing, malformed or expired token |
| `403` | Valid token without the required role |
| `404` | No route matches |
| `429` | Rate limit exceeded |
| `503` | Circuit breaker open, or no healthy instance |

## Events

None. The gateway is request/response only.

## Decisions deferred

- **Spring Cloud release train.** Must match Boot 3.5 — take it from the official
  compatibility matrix rather than guessing. Mismatched Boot/Cloud versions are the
  most common startup failure on a project like this.
- **Rate-limit backing store.** Spring Cloud Gateway's limiter normally wants Redis,
  which this project dropped. Either reintroduce Redis for this alone, or use an
  in-memory limiter and accept that it is per-instance.
- **Whether the SPA is served by the gateway** in production, or from a separate
  static host.
- **Token relay vs. re-validation cost** — measure before optimising.
