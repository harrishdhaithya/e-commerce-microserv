package com.ecommerce.common.web;

/**
 * The request is well formed, but conflicts with the current state of the resource.
 *
 * <p>Mapped to 409 by {@link GlobalExceptionHandler}. Use it for a duplicate unique
 * value, or for a delete blocked by something still referencing the row - outcomes
 * the caller can understand and act on, as opposed to a 500 that tells them only that
 * something broke.
 *
 * <p>Deliberately a plain RuntimeException with no Spring Data types in its signature:
 * common-web must stay usable by services that have no persistence layer at all.
 */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }

    public static ConflictException duplicate(String resourceType, String field, Object value) {
        return new ConflictException(
                "%s with %s '%s' already exists".formatted(resourceType, field, value));
    }

    public static ConflictException inUse(String resourceType, Object identifier, String reason) {
        return new ConflictException(
                "%s %s cannot be removed: %s".formatted(resourceType, identifier, reason));
    }
}
