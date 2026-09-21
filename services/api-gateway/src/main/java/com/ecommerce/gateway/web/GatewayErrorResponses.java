package com.ecommerce.gateway.web;

import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

/**
 * Writes the same {@code ApiError} shape the services return.
 *
 * <p>Hand-built rather than reusing common-web's record, because that module is
 * servlet-based and cannot be on a WebFlux classpath. The duplication is one JSON
 * literal, and it buys the client a single error contract across the whole platform -
 * gateway rejections included.
 */
public final class GatewayErrorResponses {

    private GatewayErrorResponses() {
    }

    public static Mono<Void> write(ServerWebExchange exchange, HttpStatus status, String message) {
        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);

        String correlationId = exchange.getResponse().getHeaders()
                .getFirst(CorrelationIdWebFilter.HEADER);

        String body = """
                {"timestamp":"%s","status":%d,"error":"%s","message":"%s","path":"%s","correlationId":%s,"violations":[]}"""
                .formatted(
                        Instant.now(),
                        status.value(),
                        status.getReasonPhrase(),
                        message,
                        exchange.getRequest().getPath().value(),
                        correlationId == null ? "null" : "\"" + correlationId + "\"");

        DataBuffer buffer = exchange.getResponse().bufferFactory()
                .wrap(body.getBytes(StandardCharsets.UTF_8));
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }
}
