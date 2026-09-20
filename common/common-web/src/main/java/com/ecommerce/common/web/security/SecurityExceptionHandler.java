package com.ecommerce.common.web.security;

import com.ecommerce.common.web.ApiError;
import com.ecommerce.common.web.CorrelationIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Translates authorization failures into the right status codes.
 *
 * <p>Without this, {@code GlobalExceptionHandler}'s catch-all
 * {@code @ExceptionHandler(Exception.class)} swallows them: a {@code @PreAuthorize}
 * rejection propagates out of the controller as
 * {@code AuthorizationDeniedException}, the catch-all matches it like any other
 * exception, and the client gets <strong>500 instead of 403</strong>.
 *
 * <p>That is worse than a cosmetic bug. A client cannot tell "you lack the role" from
 * "the service is broken", and genuine authorization denials get logged at ERROR
 * alongside real faults, which buries them.
 *
 * <p>{@code HIGHEST_PRECEDENCE} puts this advice ahead of the catch-all, so the
 * specific handler is found first.
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SecurityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(SecurityExceptionHandler.class);

    /**
     * Authenticated, but lacking the required role or ownership.
     *
     * <p>Covers Spring Security 6's {@code AuthorizationDeniedException}, which
     * extends {@code AccessDeniedException}.
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> handleAccessDenied(AccessDeniedException ex,
                                                       HttpServletRequest request) {
        String correlationId = CorrelationIdFilter.current(request);
        // Expected traffic, not a fault: WARN, and no stack trace.
        log.warn("Access denied [correlationId={}] on {} {}",
                correlationId, request.getMethod(), request.getRequestURI());

        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(
                ApiError.of(403, "Forbidden",
                        "You do not have permission to perform this action",
                        request.getRequestURI(), correlationId));
    }

    /**
     * Missing or invalid credentials reaching a controller.
     *
     * <p>Usually handled by the filter chain long before this, but a method-level
     * failure can still surface here.
     */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiError> handleAuthentication(AuthenticationException ex,
                                                         HttpServletRequest request) {
        String correlationId = CorrelationIdFilter.current(request);
        log.warn("Authentication failed [correlationId={}] on {}",
                correlationId, request.getRequestURI());

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
                ApiError.of(401, "Unauthorized", "Authentication is required",
                        request.getRequestURI(), correlationId));
    }
}
