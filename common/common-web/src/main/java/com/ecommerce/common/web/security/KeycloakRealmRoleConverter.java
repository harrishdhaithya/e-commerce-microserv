package com.ecommerce.common.web.security;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Maps Keycloak realm roles onto Spring Security authorities.
 *
 * <p>This exists because the two disagree about where roles live. Spring's default
 * converter reads a flat {@code scope} or {@code scp} claim. Keycloak nests realm
 * roles instead:
 *
 * <pre>
 * {
 *   "sub": "8f14e45f-...",
 *   "realm_access": { "roles": ["CUSTOMER", "ADMIN", "offline_access"] }
 * }
 * </pre>
 *
 * <p>Nothing bridges the two by default, so without this every
 * {@code @PreAuthorize("hasRole('ADMIN')")} returns 403 even with a perfectly valid
 * admin token. That is the single most common Keycloak/Spring integration snag, and
 * it looks like a bug in your own code.
 *
 * <p>The {@code ROLE_} prefix is added here because {@code hasRole('ADMIN')} looks
 * for an authority named {@code ROLE_ADMIN} - the expression adds the prefix
 * implicitly, so the authority must already carry it.
 */
public class KeycloakRealmRoleConverter implements Converter<Jwt, Collection<GrantedAuthority>> {

    private static final String REALM_ACCESS_CLAIM = "realm_access";
    private static final String ROLES_KEY = "roles";
    private static final String AUTHORITY_PREFIX = "ROLE_";

    @Override
    @SuppressWarnings("unchecked")
    public Collection<GrantedAuthority> convert(Jwt jwt) {
        Object realmAccess = jwt.getClaim(REALM_ACCESS_CLAIM);
        if (!(realmAccess instanceof Map<?, ?> claim)) {
            // A token with no realm_access is valid - it just has no realm roles.
            // Authenticated but unauthorized is a legitimate state, so return empty
            // rather than throwing.
            return List.of();
        }

        Object roles = ((Map<String, Object>) claim).get(ROLES_KEY);
        if (!(roles instanceof Collection<?> roleList)) {
            return List.of();
        }

        return roleList.stream()
                .filter(Objects::nonNull)
                .map(Object::toString)
                .filter(role -> !role.isBlank())
                .map(role -> (GrantedAuthority) new SimpleGrantedAuthority(AUTHORITY_PREFIX + role))
                .toList();
    }
}
