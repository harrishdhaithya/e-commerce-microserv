package com.ecommerce.customer.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Create or replace an address.
 *
 * <p>Used for both POST and PUT, so it is a full representation rather than a patch -
 * a PUT that silently kept old values for omitted fields would be surprising.
 *
 * <p>Sizes mirror the column widths in V1__create_customer_tables.sql. Validating
 * here turns what would be an opaque database error into a 400 naming the offending
 * field.
 */
public record AddressRequest(
        @Size(max = 60) String label,

        @NotBlank @Size(max = 200) String recipientName,
        @NotBlank @Size(max = 200) String line1,
        @Size(max = 200) String line2,
        @NotBlank @Size(max = 120) String city,
        @Size(max = 120) String region,
        @NotBlank @Size(max = 20) String postalCode,

        // ISO 3166-1 alpha-2. Accepts either case; the entity uppercases on the way
        // in, so "gb" and "GB" are stored identically.
        @NotBlank
        @Pattern(regexp = "^[A-Za-z]{2}$", message = "must be a 2-letter ISO country code")
        String countryCode,

        @Size(max = 40) String phone,

        boolean defaultShipping,
        boolean defaultBilling) {
}
