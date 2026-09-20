package com.ecommerce.catalog.api.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Create or replace a product. Admin only.
 *
 * <p>Sizes mirror the column widths in the migration, so a value too long becomes a
 * 400 naming the field rather than an opaque database error.
 */
public record ProductRequest(
        @NotBlank @Size(max = 64) String sku,
        @NotBlank @Size(max = 200) String name,
        @Size(max = 4000) String description,

        /*
         * Money, so BigDecimal - never a floating-point type. Digits mirrors
         * NUMERIC(19,4): a price with five decimal places is rejected here rather
         * than silently rounded on insert.
         */
        @NotNull
        @DecimalMin(value = "0.00", inclusive = true, message = "must not be negative")
        @Digits(integer = 15, fraction = 4)
        BigDecimal price,

        /** Category referenced by slug - the stable, readable key, not an internal id. */
        @NotBlank @Size(max = 140) String categorySlug,

        @Size(max = 500) String imageUrl,

        /**
         * Boxed on purpose: absent means "leave as is" on an update, and defaults to
         * active on create. A primitive would silently deactivate a product whenever
         * the field was omitted.
         */
        Boolean active) {
}
