package com.ecommerce.inventory.api.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Batch availability lookup.
 *
 * <p>Exists so a cart page is one request rather than one per line. Capped, because
 * an unbounded list is an invitation to ask for the whole catalogue in a single call.
 */
public record AvailabilityRequest(
        @NotEmpty @Size(max = 100) List<@Size(max = 64) String> skus) {
}
