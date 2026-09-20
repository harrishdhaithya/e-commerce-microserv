package com.ecommerce.catalog.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Public catalog, private administration.
 *
 * <p>This service is the one place in the platform where anonymous access is the
 * normal case: a visitor must be able to browse products without an account. So the
 * rule is inverted compared with customer-service - reads are open, and only writes
 * require a token carrying the {@code ADMIN} role.
 *
 * <p>Without this class, simply having the resource-server dependency on the
 * classpath would secure every endpoint, and the storefront would return 401 for its
 * product listing.
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
                        // Browsing is public. Matched by method as well as path, so a
                        // POST to the same URL still has to authenticate.
                        .requestMatchers(HttpMethod.GET, "/api/products/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/categories/**").permitAll()

                        .requestMatchers("/actuator/health/**", "/actuator/info").permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html")
                        .permitAll()
                        // The H2 console is dev-profile only, but the filter chain has
                        // to let it through or it is unusable even there.
                        .requestMatchers("/h2-console/**").permitAll()

                        // Everything else - the writes - needs a valid token. The role
                        // check itself sits on the controller methods via
                        // @PreAuthorize, so it is visible where the operation is.
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt ->
                        jwt.jwtAuthenticationConverter(jwtAuthenticationConverter)))
                // The H2 console renders in a frame; the default DENY breaks it.
                .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()));

        return http.build();
    }
}
