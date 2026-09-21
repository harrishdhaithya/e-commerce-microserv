# api-gateway

> ✅ **Implemented.** This document describes running code. Routes come from
> `application.yml`, the access rules from `config/SecurityConfig`.

Single entry point for the SPA. Routes requests to services, validates JWTs at the
edge, and applies cross-cutting policy (CORS, rate limiting, request logging).

| | |
|---|---|
| **Port** | 8080 |
| **Database** | **None.** The gateway is stateless by design |
| **Module** | `services/api-gateway` |
| **Package** | `com.ecommerce.gateway` |
| **Technology** | Spring Cloud Gateway 4.3 (reactive/WebFlux), Spring Cloud 2025.0.3 |

## Responsibilities

**Owns:** routing, edge JWT authentication, correlation-ID generation for requests
arriving without one.

**Not yet implemented:** rate limiting, circuit breakers, request-timing logs. Noted
below so this document is not read as describing them.

**Does not own:** business logic of any kind, and no data. A gateway that starts
aggregating or transforming domain data becomes a distributed monolith's front door.

The value it adds: the SPA knows exactly one base URL, and garbage tokens are
rejected before they cost a service a thread.

## Routing table

Routes are keyed on **resource** paths, not service names — see
[URL conventions](../architecture.md). A service owning two resources therefore gets
two routes, which is the price of keeping service topology out of the public API.

**Implemented today:**

| Route id | Path | Target | Edge rule |
|---|---|---|---|
| `products` | `/api/products/**` | catalog-service:8082 | `GET` public, else authenticated |
| `categories` | `/api/categories/**` | catalog-service:8082 | `GET` public, else authenticated |
| `customers` | `/api/customers/**` | customer-service:8081 | Authenticated |
| — | `/actuator/health`, `/actuator/info` | gateway's own | Public |
| — | `/actuator/gateway/routes` | gateway's own | Authenticated — lists internal service addresses |

**Planned as their services arrive:** `/api/cart/**` (8084), `/api/stock/**` and
`/api/reservations/**` (8083), `/api/orders/**` (8085), `/api/payments/**` (8086),
`/api/notifications/**` (8087).

Targets come from `CATALOG_SERVICE_URI` and `CUSTOMER_SERVICE_URI`, defaulting to
localhost so the same jar runs from an IDE without reconfiguration.

Targets resolve by container DNS rather than a discovery server — see
[architecture.md](../architecture.md).

Route configuration lives under `spring.cloud.gateway.server.webflux.routes`. Gateway
4.2 moved it there from `spring.cloud.gateway.routes`; both still bind, but the older
form is legacy. `fail-on-route-definition-error: true` turns a malformed route into a
startup failure rather than a silent 404 later.

**When it is added, `/api/reservations/**` should not be routed publicly at all.** Reservations are
created by the saga, not by clients; exposing them would let a caller reserve stock
without placing an order. Either omit the route or restrict it to internal callers.

## Security posture

### The gateway authenticates; services authorize

This split is the central decision. Here a token is checked for a valid signature,
issuer and expiry, so garbage is rejected before it costs a downstream service a
thread. **Role checks stay on the services**, next to the operations they protect.

Consequently there is no Keycloak role converter in this module. Duplicating the
realm-role parsing here would imply the gateway is where authorization decisions are
made, which is exactly the coupling to avoid — and a service must never trust a caller
merely because the call arrived from inside the network.

So the gateway's rules are coarse: public, or authenticated. `@PreAuthorize` on a
service method is what distinguishes `CUSTOMER` from `ADMIN`.

```yaml
spring.security.oauth2.resourceserver.jwt:
  issuer-uri: ${KEYCLOAK_ISSUER_URI:http://localhost:8180/realms/ecommerce}
  jwk-set-uri: ${KEYCLOAK_JWK_SET_URI:.../protocol/openid-connect/certs}
```

Same split as the services: validate `iss` as the browser sees it, fetch the signing
keys over whichever network this process is on. Validation is then offline against
cached JWKS, so Keycloak being down does not break authenticated traffic.

Matching is **by method as well as path**, so `GET /api/products` is public while
`POST` to the same path is not.

### Why reactive, and why no common-web

Spring Cloud Gateway's primary form is reactive (WebFlux). That rules out depending on
`common-web`, which is built on `spring-boot-starter-web` — mixing servlet and WebFlux
in one application makes Boot pick the servlet stack and the gateway stops routing.

The consequence is a small, deliberate duplication: the `ApiError` JSON shape and the
correlation-ID filter are reimplemented here in reactive form. Roughly 60 lines,
versus either forking `common-web` or using the servlet gateway. The correlation-ID
filter also skips MDC, because in a reactive pipeline a ThreadLocal MDC would attach
the ID to whichever unrelated request next occupied that thread.

## Filters

| Filter | Status |
|---|---|
| Correlation ID — generate if absent, propagate downstream, echo in the response | ✅ |
| JWT validation — reject malformed/expired/wrong-issuer with `401` | ✅ |
| Rate limiting | Not implemented — see deferred decisions |
| Circuit breaker | Phase 3, with Resilience4j |
| Request timing logs | Not implemented |

## Error responses

The gateway returns the same `ApiError` shape as every service, so the SPA needs one
error handler rather than a special case for the edge. Custom
`authenticationEntryPoint` and `accessDeniedHandler` produce it; without them a
rejection would return a bare status with an empty body.

```json
{
  "timestamp": "2026-09-21T09:42:08.158124425Z",
  "status": 401,
  "error": "Unauthorized",
  "message": "Authentication is required",
  "path": "/api/customers/me",
  "correlationId": "8bb5c489-7b2b-4376-926c-b55e3c21b97a",
  "violations": []
}
```

| Status | Cause |
|---|---|
| `401` | Missing, malformed, expired or wrong-issuer token |
| `404` | No route matches |
| `5xx` | Downstream service unreachable |

Note that `403` does **not** originate here — role checks live on the services, so a
role-based denial is produced downstream and passed through.

## Events

None. The gateway is request/response only.

## Decisions deferred

- **Rate-limit backing store.** Spring Cloud Gateway's limiter normally wants Redis,
  which this project dropped. Either reintroduce Redis for this alone, or use an
  in-memory limiter and accept that it is per-instance.
- **Whether the SPA is served by the gateway** in production, or from a separate
  static host.
- **Token relay vs. re-validation cost** — measure before optimising.
- **Whether to forward resolved claims downstream** as headers, saving services a
  second parse. Tempting, but a service that trusts a header instead of the token is
  trusting the network.

Settled since: Spring Cloud **2025.0.3** pairs with Boot 3.5 (its
`spring-cloud-starter-parent` declares Boot 3.5.15). The 2025.1.x train targets Boot
4 — mismatching these is the most common startup failure on a project like this.
