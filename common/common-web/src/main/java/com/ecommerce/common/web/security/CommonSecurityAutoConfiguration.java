package com.ecommerce.common.web.security;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;

/**
 * Supplies the Keycloak role mapping to any service that is a resource server.
 *
 * <p>{@code @ConditionalOnClass(Jwt.class)} keeps this dormant where Spring Security's
 * OAuth2 classes are absent. common-web declares that dependency as optional, so
 * catalog-service - which has no resource-server starter - never sees these beans and
 * its endpoints stay public.
 *
 * <p>Note that defining a {@link JwtAuthenticationConverter} bean is not enough on its
 * own: Spring Boot does not wire it into the filter chain for you. A service has to
 * pass it explicitly, e.g.
 * {@code .oauth2ResourceServer(o -> o.jwt(j -> j.jwtAuthenticationConverter(converter)))}.
 * Providing the bean here just means no service writes the claim-parsing twice.
 */
@AutoConfiguration
@ConditionalOnClass(Jwt.class)
public class CommonSecurityAutoConfiguration {

    /**
     * Ensures a {@code @PreAuthorize} rejection returns 403 rather than being caught
     * by the catch-all handler and reported as 500.
     */
    @Bean
    @ConditionalOnMissingBean
    public SecurityExceptionHandler securityExceptionHandler() {
        return new SecurityExceptionHandler();
    }

    @Bean
    @ConditionalOnMissingBean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(new KeycloakRealmRoleConverter());
        // Principal name comes from `sub` by default, which is exactly the value
        // customer rows are keyed on.
        converter.setPrincipalClaimName("sub");
        return converter;
    }
}
