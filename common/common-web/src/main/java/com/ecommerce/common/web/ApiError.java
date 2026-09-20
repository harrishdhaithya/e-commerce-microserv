package com.ecommerce.common.web;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * The single error shape every service returns. One contract means the Angular
 * client needs exactly one error handler, not one per service.
 */
public record ApiError(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path,
        String correlationId,
        List<FieldViolation> violations) {

    public record FieldViolation(String field, String message) {
    }

    public static ApiError of(int status, String error, String message, String path, String correlationId) {
        return new ApiError(Instant.now(), status, error, message, path, correlationId, List.of());
    }

    public static ApiError validation(String path, String correlationId, Map<String, String> fieldErrors) {
        List<FieldViolation> violations = fieldErrors.entrySet().stream()
                .map(e -> new FieldViolation(e.getKey(), e.getValue()))
                .toList();
        return new ApiError(Instant.now(), 400, "Bad Request",
                "Validation failed", path, correlationId, violations);
    }
}
