package com.ecommerce.customer.api.dto;

import jakarta.validation.constraints.Size;

/**
 * Partial profile update.
 *
 * <p>Every field is nullable and null means "leave unchanged" - this backs a PATCH,
 * not a PUT, so a client sending only {@code phone} must not blank out their name.
 *
 * <p>{@code marketingOptIn} is a boxed {@link Boolean} for exactly that reason: a
 * primitive would default to {@code false} when omitted and silently opt the customer
 * out of marketing they had agreed to.
 *
 * <p>Email and roles are not here. Those belong to Keycloak, and a customer changes
 * them through its account console.
 */
public record UpdateCustomerRequest(
        @Size(max = 100) String firstName,
        @Size(max = 100) String lastName,
        @Size(max = 40) String phone,
        Boolean marketingOptIn) {
}
