# Build guide: Keycloak + customer-service

A step-by-step plan for implementing Phase 1 yourself. Each step ends in a
**checkpoint** — something observable. Don't move on until the checkpoint passes;
that's what keeps a bug from being three steps behind where you notice it.

Target: [docs/services/customer-service.md](../services/customer-service.md) is the
spec. [catalog-service](../../services/catalog-service) is your template.

**Order matters.** Steps 1–3 are Keycloak with no new service; steps 4–9 are the
service. Doing them together means debugging OIDC and a new module at the same time.

---

## Step 1 — Keycloak running

Add Keycloak to `infra/docker-compose.yml`. Use the `start-dev` command — it runs on
its own embedded database, so there's nothing to provision.

- Image: `quay.io/keycloak/keycloak`
- Port **8180**:8080 (8080 is reserved for the gateway)
- Admin credentials via `KC_BOOTSTRAP_ADMIN_USERNAME` / `KC_BOOTSTRAP_ADMIN_PASSWORD`
  env vars (the older `KEYCLOAK_ADMIN*` names were renamed — check the image docs for
  the version you pull)

**Checkpoint:** you can log into the admin console at http://localhost:8180 .

---

## Step 2 — Realm, roles, users, clients

In the admin console, create:

| Thing | Value |
|---|---|
| Realm | `ecommerce` |
| Realm roles | `CUSTOMER`, `ADMIN` |
| User | `customer@test.local`, password set, non-temporary, role `CUSTOMER` |
| User | `admin@test.local`, password set, non-temporary, roles `CUSTOMER` + `ADMIN` |
| Client | `ecom-web` — public, **Standard flow** on, PKCE required (S256) |
| Client | `ecom-dev-cli` — public, **Direct access grants** on |

Set redirect URIs on `ecom-web` to `http://localhost:4200/*` and web origins to
`http://localhost:4200`.

### Why two clients

`ecom-web` is what the browser uses: authorization-code + PKCE, no secret, because a
SPA cannot keep one. But that flow needs a browser, which makes `curl` testing
painful. `ecom-dev-cli` with direct access grants (the password grant) lets you fetch
a token from the command line in one call. **It is a dev convenience only** — never
enable direct access grants on a client you'd ship.

### Then export the realm immediately

```
docker compose exec keycloak /opt/keycloak/bin/kc.sh export \
  --dir /tmp/export --realm ecommerce --users realm_file
```

Copy it out to `infra/keycloak/realm-ecommerce.json` and **commit it**. Mount it and
add `--import-realm` to the command so the realm rebuilds on startup.

Do this now, not later. Without it, every `docker compose down -v` costs you twenty
minutes of clicking, and you will do that more than once. The test users' passwords
belong in version control here — this realm is local-dev only.

**Checkpoint:** `docker compose down -v && docker compose up` and your realm, roles
and users are all still there.

---

## Step 3 — Get a token and read it

```
curl -s -X POST http://localhost:8180/realms/ecommerce/protocol/openid-connect/token \
  -d grant_type=password -d client_id=ecom-dev-cli \
  -d username=customer@test.local -d password=<password>
```

Take the `access_token` and decode it (paste at jwt.io, or `cut -d. -f2 | base64 -d`).

**Find these claims and understand them before continuing:**

| Claim | Why it matters |
|---|---|
| `sub` | The stable user id. **This becomes `customers.keycloak_id`** |
| `iss` | Must exactly match your `issuer-uri` later — see the trap in Step 5 |
| `realm_access.roles` | Your roles, in a nested structure Spring does not read by default |
| `email`, `given_name`, `family_name` | What JIT provisioning will populate from |
| `exp` | Short-lived. Refresh is the library's job, not yours |

If `email` is missing, add the `email` client scope to the client's assigned scopes.

**Checkpoint:** you can explain what `sub` is and why it, rather than `email`, is the
key you'll store.

---

## Step 4 — Scaffold the module

Copy the shape of `catalog-service`. Create `services/customer-service/` with a POM
(same dependencies, minus springdoc if you like, plus
`spring-boot-starter-oauth2-resource-server`), and add it to `services/pom.xml`
`<modules>`.

Then: `application.yml` (port **8081**, `ddl-auto: validate`, Hikari max 5),
`application-dev.yml` (`jdbc:h2:file:./data/customer`, H2 console),
`application-test.yml` (in-memory), `CustomerServiceApplication`, and
`V1__create_customer_tables.sql` with `customers` and `addresses` per the spec.

Write the entity, repository, and a context-load test **before any endpoint**.

**Checkpoint:** `../../mvnw test` passes. That one test runs your migration and has
Hibernate validate your entity against it — so a column-name typo fails here rather
than at runtime three steps later.

---

## Step 5 — Resource server

Add to `application.yml`:

```yaml
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: http://localhost:8180/realms/ecommerce
```

Add a `SecurityFilterChain` bean: `/actuator/health` public, everything else
authenticated, `oauth2ResourceServer(oauth2 -> oauth2.jwt(...))`, CSRF disabled (this
is a stateless token API, not a cookie-session app), and session management
`STATELESS`.

Add a temporary `GET /api/customers/whoami` returning the `sub` from an injected
`@AuthenticationPrincipal Jwt`. You'll delete it in Step 7.

### ⚠️ The trap that will cost you an hour

**`issuer-uri` must match the token's `iss` claim byte for byte.**

If Keycloak is in Docker and your service runs on the host, the token says
`http://localhost:8180/realms/ecommerce`. But if a service *inside* Docker fetches
from `http://keycloak:8080/...`, the issuer won't match and you get an opaque
`invalid_token` with no useful message.

Fix by setting `KC_HOSTNAME_URL` (or `--hostname`) on Keycloak so it always issues
`localhost:8180` as the issuer, regardless of who asks. Decide this now — it bites
again in Phase 2 when the gateway arrives.

**Checkpoint:**
- `curl /api/customers/whoami` with no token → **401**
- with `-H "Authorization: Bearer <token>"` → **200** and your `sub`

---

## Step 6 — Role mapping

Try `@PreAuthorize("hasRole('ADMIN')")` on a test endpoint with your admin token. It
returns **403**. This is the single most common Keycloak/Spring snag, and it is not a
bug in your code.

Spring's default converter reads roles from a `scope`/`scp` claim. Keycloak puts them
in `realm_access.roles`, nested. Nothing bridges the two by default.

Write a `JwtAuthenticationConverter` that pulls `realm_access.roles` and maps each to
a `SimpleGrantedAuthority` with the **`ROLE_` prefix** (`hasRole('ADMIN')` looks for
`ROLE_ADMIN` — the prefix is added implicitly by `hasRole`, not by you).

**Put it in `common-web`**, registered by `CommonWebAutoConfiguration`, so every
future service inherits it. That is exactly what that module is for. Guard it with
`@ConditionalOnClass(Jwt.class)` so `catalog-service` doesn't break — it has no
resource-server dependency yet.

Also add `@EnableMethodSecurity`, or `@PreAuthorize` is silently ignored — which is
worse than an error, because it fails *open*.

**Checkpoint:**
- admin token on an `ADMIN` endpoint → 200
- customer token on the same endpoint → **403** (not 401 — the token is valid, the
  role is missing)

---

## Step 7 — JIT provisioning and `/me`

Now the real logic. `GET /api/customers/me`:

1. Read `sub` from the JWT.
2. `findByKeycloakId(sub)`.
3. If absent, create a customer from the token claims (`sub`, `email`, `given_name`,
   `family_name`) and save.
4. Return the DTO.

Delete `whoami`.

Two things to get right:

- **Never read the customer id from a request parameter.** It comes from the token or
  it doesn't come at all. A `GET /api/customers/{id}` that trusts its path variable
  is how one customer reads another's data.
- **Handle the race.** Two simultaneous first requests can both see "absent" and both
  insert. The unique constraint on `keycloak_id` catches it; catch
  `DataIntegrityViolationException` and re-read. This is why the constraint exists
  rather than just an application-level check.

Then `PATCH /api/customers/me` for `firstName`, `lastName`, `phone`,
`marketingOptIn`. Email and roles are **not** editable — they live in Keycloak.

**Checkpoint:** first `GET /me` creates a row (confirm in the H2 console at
`jdbc:h2:file:./data/customer`); the second returns the same `publicId`.

---

## Step 8 — Addresses

Full CRUD under `/api/customers/me/addresses`, per the spec.

The interesting parts, in order of how easy they are to get wrong:

1. **Ownership on every single operation.** Load the address *and* check it belongs to
   the caller's customer. A repository method like
   `findByPublicIdAndCustomerKeycloakId(...)` makes this structural rather than
   something you have to remember in each handler. Forget it once and you have a real
   vulnerability.
2. **404 vs 403 for someone else's address.** Returning 403 confirms the id exists,
   which leaks information. Prefer 404.
3. **Default shipping/billing.** Setting a new default must clear the old one in the
   same transaction, or you end up with two defaults and no rule for which wins.
4. **Validation.** `@Valid` with Bean Validation on the request DTO. `country_code`
   as exactly 2 uppercase letters, `postal_code` non-blank. Your `GlobalExceptionHandler`
   already turns violations into the shared `ApiError` shape with a `violations` array
   — you get that for free.

**Checkpoint:** with two tokens (customer and admin), customer A cannot read, update
or delete an address belonging to admin. Write that as a test, not a manual check.

---

## Step 9 — Tests

**Do not start Keycloak for tests.** Use `spring-security-test`:

```java
mockMvc.perform(get("/api/customers/me")
    .with(jwt().jwt(j -> j.claim("sub", "test-user-1").claim("email", "a@b.local"))
               .authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"))));
```

This keeps the suite in the milliseconds range, which is what keeps you running it.

Cover at least:

- `/me` provisions on first call, reuses on second
- 401 with no token
- 403 with a valid token lacking the role
- address CRUD round-trip
- **cross-customer access is denied** — the one that matters most
- validation rejection produces `violations` in the response
- setting a new default clears the previous one

Real browser login gets covered once in the Phase 5 Playwright E2E, not here.

---

## Step 10 — Angular

`angular-auth-oidc-client` against `ecom-web`. Authorization-code + PKCE, access token
in memory — **not localStorage**, which is readable by any injected script.

- An interceptor attaching `Authorization: Bearer` to `/api` calls only. Never send
  your token to a third-party host.
- A route guard on `/account`.
- An account page reading and editing `/me` plus addresses.
- Handle 401 by redirecting to login, not by showing a raw error.

**Checkpoint:** click Login, land on Keycloak's own page, sign in, return
authenticated, edit an address, reload — it persisted.

---

## Definition of done

- [ ] `docker compose down -v && up` restores the realm from the committed JSON
- [ ] `./mvnw clean install` green from the repo root
- [ ] 401 without a token, 403 without the role, 200 with both
- [ ] `/me` provisions exactly one row per Keycloak user
- [ ] A test proves cross-customer access is denied
- [ ] Browser login works end to end
- [ ] **Stop Keycloak and confirm authenticated API calls still succeed** — the
      offline-JWT-validation lesson, and the best single demonstration that you
      understand what a JWT is
- [ ] `docs/services/customer-service.md` banner changed to ✅ and its schema made to
      match your migration exactly

---

## Where to ask for a review

Good moments to stop and have the code looked at:

- **After Step 6** — security configuration is the easiest thing to get subtly wrong,
  and the failure mode is silent.
- **After Step 8** — ownership enforcement deserves a second pair of eyes.
- **Before marking done** — a check that the docs match what you actually built.

## If you get stuck

- `401` with a valid-looking token → issuer mismatch (Step 5 trap). Compare `iss` in
  the decoded token against your `issuer-uri`, character by character.
- `403` everywhere → role mapping (Step 6), or a missing `@EnableMethodSecurity`.
- `@PreAuthorize` seems ignored → missing `@EnableMethodSecurity`. Fails open, so test
  the negative case deliberately.
- Hibernate validation errors on startup → your entity and migration disagree. The
  message names the column.
- Token has no `email` → add the `email` client scope in Keycloak.
