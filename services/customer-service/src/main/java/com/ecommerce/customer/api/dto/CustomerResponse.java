package com.ecommerce.customer.api.dto;

import com.ecommerce.customer.domain.Customer;

import java.time.Instant;
import java.util.UUID;

/**
 * What the API returns for a customer.
 *
 * <p>Note what is absent: the internal {@code id}, the {@code version}, and
 * {@code keycloakId}. The last one is deliberate - exposing another system's user
 * identifier invites clients to start keying their own data on it, which couples them
 * to your identity provider.
 */
public record CustomerResponse(
        UUID id,
        String email,
        String firstName,
        String lastName,
        String phone,
        boolean marketingOptIn,
        String loyaltyTier,
        Instant createdAt) {

    public static CustomerResponse from(Customer customer) {
        return new CustomerResponse(
                customer.getPublicId(),
                customer.getEmail(),
                customer.getFirstName(),
                customer.getLastName(),
                customer.getPhone(),
                customer.isMarketingOptIn(),
                customer.getLoyaltyTier(),
                customer.getCreatedAt());
    }
}
