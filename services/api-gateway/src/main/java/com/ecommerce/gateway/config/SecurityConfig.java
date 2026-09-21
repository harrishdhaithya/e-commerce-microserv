package com.ecommerce.gateway.config;

import com.ecommerce.gateway.web.GatewayErrorResponses;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import reactor.core.publisher.Mono;

/**
 * Edge authentication.
 *
 * <p><strong>The gateway authenticates; services authorize.</strong> That split is
 * deliberate. Here a token is checked for a valid signature, issuer and expiry, so
 * garbage is rejected before it costs a downstream service a thread. Role checks stay
 * on the services, next to the operations they protect, because a service must never
 * trust a caller merely because the call arrived from inside the network.
 *
 * <p>So there is no Keycloak role converter in this module. Duplicating the
 * realm-role parsing here would imply the gateway is the place authorization
 * decisions are made, which is precisely the coupling to avoid.
 */
@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        return http
                // No cookies, no sessions: a bearer token is never attached by the
                // browser automatically, which is what CSRF protection guards against.
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)

                .authorizeExchange(exchange -> exchange
                        // Browsing the catalog is public - the same rule
                        // catalog-service enforces for itself. Matched by method, so a
                        // POST to the same path still authenticates.
                        .pathMatchers(HttpMethod.GET, "/api/products/**").permitAll()
                        .pathMatchers(HttpMethod.GET, "/api/categories/**").permitAll()

                        // Carts are usable before signing up, so the edge lets them
                        // through and cart-service resolves ownership per request -
                        // a bearer token's `sub`, or the X-Cart-Token header. Merging
                        // is the exception and is authenticated downstream.
                        .pathMatchers("/api/cart/**").permitAll()

                        .pathMatchers("/actuator/health/**", "/actuator/info").permitAll()

                        // CORS preflight carries no credentials and must not 401.
                        .pathMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                        .anyExchange().authenticated())

                .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()))

                // Without these, rejections return an empty body with a bare status.
                // Matching the services' ApiError shape means the SPA needs one error
                // handler rather than a special case for the gateway.
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint((exchange, denied) ->
                                GatewayErrorResponses.write(exchange, HttpStatus.UNAUTHORIZED,
                                        "Authentication is required"))
                        .accessDeniedHandler((exchange, denied) ->
                                GatewayErrorResponses.write(exchange, HttpStatus.FORBIDDEN,
                                        "You do not have permission to perform this action")))
                .build();
    }
}
