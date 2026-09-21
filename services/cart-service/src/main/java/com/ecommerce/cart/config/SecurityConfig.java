package com.ecommerce.cart.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Carts are reachable without an account.
 *
 * <p>This is the awkward service, security-wise. catalog-service is public and
 * customer-service is authenticated; carts are <em>both</em>, because a shopper must
 * be able to fill a basket before signing up, and the same endpoints then serve them
 * once they do.
 *
 * <p>So the endpoints permit anonymous access, and ownership is resolved per request:
 * a bearer token means the cart belongs to that {@code sub}, and its absence means it
 * belongs to the {@code X-Cart-Token} the SPA supplies. The controller enforces that
 * one or the other is present - see {@code CartController}.
 *
 * <p>The security consequence is worth stating plainly: an anonymous cart is only as
 * private as its token. That is acceptable because a cart holds no personal data -
 * just product ids - and it is why {@code /api/cart/merge} requires authentication.
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http,
                                           JwtAuthenticationConverter jwtAuthenticationConverter)
            throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Requires a real token: merging is what binds an anonymous
                        // basket to an identity, so it cannot be done anonymously.
                        .requestMatchers("/api/cart/merge").authenticated()

                        // Everything else is open to anonymous shoppers.
                        .requestMatchers("/api/cart/**").permitAll()

                        .requestMatchers("/actuator/health/**", "/actuator/info").permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html")
                        .permitAll()
                        .requestMatchers("/h2-console/**").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt ->
                        jwt.jwtAuthenticationConverter(jwtAuthenticationConverter)))
                .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()));

        return http.build();
    }
}
