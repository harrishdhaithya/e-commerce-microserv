package com.ecommerce.customer.service;

import org.springframework.security.oauth2.jwt.Jwt;

/**
 * The claims this service cares about, lifted out of a validated JWT.
 *
 * <p>Exists so the service layer never handles a {@link Jwt}. Controllers translate
 * the token into this record and pass it down, which keeps the business logic
 * testable without any security scaffolding and makes swapping identity providers a
 * change in one place.
 *
 * @param subject Keycloak's {@code sub} - the only identifier that is stable
 */
public record CustomerIdentity(String subject, String email, String firstName, String lastName) {

    public static CustomerIdentity fromJwt(Jwt jwt) {
        return new CustomerIdentity(
                jwt.getSubject(),
                jwt.getClaimAsString("email"),
                jwt.getClaimAsString("given_name"),
                jwt.getClaimAsString("family_name"));
    }
}
