package com.ecommerce.gateway.web;

import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Mints a correlation ID at the edge if the caller did not supply one, and passes it
 * downstream.
 *
 * <p>This is the reactive twin of common-web's servlet filter. The gateway is the
 * right place to generate it: every request enters here, so from this point on a
 * single ID ties the SPA's request to lines in both services' logs.
 *
 * <p>Deliberately no MDC. In a reactive pipeline the work is not pinned to the
 * request thread, so a ThreadLocal-based MDC would attach the ID to whichever
 * unrelated request happened to occupy that thread. Propagating the header is the
 * useful part; per-line logging arrives properly with OpenTelemetry in Phase 3.
 */
@Component
public class CorrelationIdWebFilter implements WebFilter, Ordered {

    public static final String HEADER = "X-Correlation-Id";

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String existing = exchange.getRequest().getHeaders().getFirst(HEADER);
        String correlationId = (existing == null || existing.isBlank())
                ? UUID.randomUUID().toString()
                : existing;

        ServerWebExchange mutated = exchange.mutate()
                .request(builder -> builder.header(HEADER, correlationId))
                .build();
        mutated.getResponse().getHeaders().set(HEADER, correlationId);

        return chain.filter(mutated);
    }
}
