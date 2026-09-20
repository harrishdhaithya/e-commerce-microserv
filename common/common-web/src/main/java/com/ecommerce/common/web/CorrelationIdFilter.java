package com.ecommerce.common.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Accepts an inbound X-Correlation-Id or mints one, then puts it in the SLF4J MDC
 * so every log line for this request carries it.
 *
 * <p>This is the cheap half of request tracing and it pays off the moment there is
 * more than one service. Full distributed tracing arrives in Phase 3; until then
 * this is what lets you follow a single request through the logs.
 */
public class CorrelationIdFilter extends OncePerRequestFilter implements Ordered {

    public static final String HEADER = "X-Correlation-Id";
    public static final String MDC_KEY = "correlationId";

    @Override
    public int getOrder() {
        // Before anything that logs, so no log line is missing the ID.
        return Ordered.HIGHEST_PRECEDENCE;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String correlationId = request.getHeader(HEADER);
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        }

        MDC.put(MDC_KEY, correlationId);
        request.setAttribute(MDC_KEY, correlationId);
        response.setHeader(HEADER, correlationId);

        try {
            chain.doFilter(request, response);
        } finally {
            // Servlet threads are pooled and reused - a stale MDC would leak the ID
            // into the next, unrelated request.
            MDC.remove(MDC_KEY);
        }
    }

    /** Correlation ID for the current request, or "unknown" outside one. */
    public static String current(HttpServletRequest request) {
        Object value = request.getAttribute(MDC_KEY);
        return value instanceof String s ? s : "unknown";
    }
}
