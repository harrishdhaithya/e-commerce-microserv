package com.ecommerce.common.web;

/**
 * Thrown when a requested resource does not exist. Mapped to 404 by
 * {@link GlobalExceptionHandler}, so services never build that response themselves.
 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }

    public static ResourceNotFoundException of(String resourceType, Object identifier) {
        return new ResourceNotFoundException("%s not found: %s".formatted(resourceType, identifier));
    }
}
