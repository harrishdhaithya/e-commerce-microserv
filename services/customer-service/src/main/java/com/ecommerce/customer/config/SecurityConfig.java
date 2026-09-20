package com.ecommerce.customer.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Resource-server configuration.
 *
 * <p>This service validates every request's JWT itself rather than trusting the
 * gateway. Not redundant: a service must never accept a caller merely because the
 * call arrived from inside the network.
 */
@Configuration
// Without this, @PreAuthorize is silently ignored - and it fails OPEN, which is worse
// than an error. Always test the negative case.
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http,
                                           JwtAuthenticationConverter jwtAuthenticationConverter)
            throws Exception {
        http
                // No cookies, no sessions: CSRF protection guards against a browser
                // silently attaching credentials, and a bearer token is never sent
                // that way.
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health/**", "/actuator/info").permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html")
                        .permitAll()
                        // Everything else needs a valid token. Role checks live on the
                        // methods, via @PreAuthorize.
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt ->
                        // The converter comes from common-web and maps Keycloak's
                        // realm_access.roles onto ROLE_-prefixed authorities. Spring
                        // Boot does not wire a JwtAuthenticationConverter bean in
                        // automatically - it has to be passed here explicitly.
                        jwt.jwtAuthenticationConverter(jwtAuthenticationConverter)));

        return http.build();
    }
}
