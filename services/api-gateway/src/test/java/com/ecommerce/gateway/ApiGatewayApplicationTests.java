package com.ecommerce.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Edge behaviour: which requests get through security, and which are rejected here
 * rather than downstream.
 *
 * <p>The routes point at ports with nothing listening, so a request that passes
 * security fails with a 5xx from the proxy attempt. That is the signal being asserted:
 * reaching the proxy at all means security let it through. Whether the downstream
 * service then answers correctly is that service's own test suite's job.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class ApiGatewayApplicationTests {

    @Autowired
    private WebTestClient client;

    @Autowired
    private RouteLocator routeLocator;

    @Test
    void contextLoadsAndRoutesResolve() {
        List<String> ids = routeLocator.getRoutes()
                .map(route -> route.getId())
                .collectList()
                .block();

        assertTrue(ids != null && ids.contains("products"),
                "route definitions must resolve at startup; got " + ids);
    }

    @Test
    void publicCatalogReadsPassThrough() {
        // Not 401: security permitted it and the gateway attempted to proxy.
        client.get().uri("/api/products?size=1")
                .exchange()
                .expectStatus().value(status -> assertNotEquals(401, status));
    }

    @Test
    void catalogWritesAreRejectedAtTheEdge() {
        // Matched by method as well as path, so the public GET rule does not leak
        // into POST.
        client.post().uri("/api/products")
                .header("Content-Type", "application/json")
                .bodyValue("{}")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void customerRoutesRequireAuthentication() {
        client.get().uri("/api/customers/me")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void rejectionsUseTheSharedApiErrorShape() {
        // The SPA has one error handler; a gateway rejection must not be a special
        // case for it.
        client.get().uri("/api/customers/me")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectHeader().exists("X-Correlation-Id")
                .expectBody()
                .jsonPath("$.status").isEqualTo(401)
                .jsonPath("$.error").isEqualTo("Unauthorized")
                .jsonPath("$.path").isEqualTo("/api/customers/me")
                .jsonPath("$.correlationId").exists();
    }

    @Test
    void mintsACorrelationIdWhenTheCallerSendsNone() {
        client.get().uri("/api/customers/me")
                .exchange()
                .expectHeader().exists("X-Correlation-Id");
    }

    @Test
    void preservesAnInboundCorrelationId() {
        client.get().uri("/api/customers/me")
                .header("X-Correlation-Id", "trace-from-client")
                .exchange()
                .expectHeader().valueEquals("X-Correlation-Id", "trace-from-client");
    }

    @Test
    void healthIsPublic() {
        client.get().uri("/actuator/health")
                .exchange()
                .expectStatus().isOk();
    }
}
