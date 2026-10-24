# Architecture diagram

```mermaid
flowchart TD
    SHOPPER(["Shopper"])

    SPA["Angular SPA<br/>catalog · account · OIDC login"]
    NGINX["nginx :4200<br/>static files + /api proxy"]

    KC["Keycloak :8180<br/>realm: ecommerce"]

    SHOPPER --> SPA
    SPA -->|"login: authorization code + PKCE"| KC
    SPA -->|"REST<br/>Authorization: Bearer …"| NGINX
    NGINX -->|"/api/**"| GW

    subgraph edge["Edge — authenticates only, never authorizes"]
        GW["api-gateway :8080<br/>routing · correlation id"]
        VALID{"JWT valid?<br/>signature · issuer · expiry"}
        GW --> VALID
    end

    VALID -->|"no → 401 ApiError"| SPA

    subgraph svcs["Services — one database each, never shared"]
        CUST["customer-service :8081<br/>profile · addresses"]
        CAT["catalog-service :8082<br/>products · categories"]
        CART["cart-service :8084<br/>carts · line items"]

        CUSTDB[("H2<br/>customer")]
        CATDB[("H2<br/>catalog")]
        CARTDB[("H2<br/>cart")]

        CUST --- CUSTDB
        CAT --- CATDB
        CART --- CARTDB
    end

    VALID -->|"/api/customers/**"| CUST
    VALID -->|"/api/products · /api/categories"| CAT
    VALID -->|"/api/cart/**"| CART

    CART -->|"product name + price<br/>synchronous · 2s timeout"| CAT

    KC -.->|"JWKS public keys<br/>fetched once, then cached"| GW

    subgraph planned["Planned — Phase 2 / 3"]
        INV["inventory-service :8083<br/>stock · reservations"]
        ORD["order-service :8085<br/>orders · saga orchestration"]
        PAY["payment-service :8086<br/>simulated gateway"]
        NOTIF["notification-service :8087"]
        KAFKA{{"Kafka"}}

        ORD -.->|"transactional outbox"| KAFKA
        KAFKA -.->|"StockReserved · PaymentAuthorized"| ORD
        KAFKA -.-> INV
        KAFKA -.-> PAY
        KAFKA -.-> NOTIF
    end

    VALID -.->|"/api/orders"| ORD
    VALID -.->|"/api/stock"| INV

    classDef client fill:#ddd6f3,stroke:#6b5bab,color:#16161d
    classDef service fill:#e8e2f7,stroke:#7c6bb5,color:#16161d
    classDef store fill:#f2eefb,stroke:#9a8cc7,color:#16161d
    classDef identity fill:#d9ecdb,stroke:#4f8a57,color:#16161d
    classDef decision fill:#fdf3d0,stroke:#b89b3e,color:#16161d
    classDef future fill:#ececf0,stroke:#9b9ba6,color:#4a4a55,stroke-dasharray: 5 5

    class SHOPPER,SPA,NGINX client
    class GW,CUST,CAT,CART service
    class CUSTDB,CATDB,CARTDB store
    class KC identity
    class VALID decision
    class INV,ORD,PAY,NOTIF,KAFKA future

    style edge fill:#fffbe8,stroke:#d4c56a,color:#16161d
    style svcs fill:#f6f4fc,stroke:#9a8cc7,color:#16161d
    style planned fill:#f4f4f6,stroke:#b4b4bd,color:#4a4a55,stroke-dasharray: 6 4
```

Everything solid is implemented and running. The dashed group is designed but not
built — no message broker exists yet.

Three things the diagram is drawn to show:

- **Keycloak is not in the request path.** The dotted line into the gateway is a
  one-time public-key fetch, cached afterwards. Keycloak going down blocks new logins,
  not authenticated traffic.
- **The edge authenticates; services authorize.** The gateway checks that a token is
  genuine and rejects garbage early. Role checks (`@PreAuthorize`) live on the service
  methods, because a service must never trust a caller merely because the call came
  from inside the network.
- **Reads are synchronous, writes with side effects are events.** The only solid
  service-to-service arrow is `cart → catalog`, a read with a timeout. Every write path
  in the unbuilt half goes through Kafka instead.

Reasoning behind these choices: [architecture.md](architecture.md). Roadmap:
[PLAN.md](../PLAN.md).
