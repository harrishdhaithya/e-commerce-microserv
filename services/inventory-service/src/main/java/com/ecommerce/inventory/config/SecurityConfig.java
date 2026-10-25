package com.ecommerce.inventory.config;

import com.ecommerce.inventory.domain.InsufficientStockException;
import com.ecommerce.common.web.ApiError;
import com.ecommerce.common.web.CorrelationIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Public availability, administered stock.
 *
 * <p>Same inverted posture as catalog-service: a storefront must show "in stock" to an
 * anonymous visitor, so the availability reads are open and everything else is not.
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
                        // Public: a single SKU's availability, and the batch form a
                        // cart page uses. Matched by method, so the PUT on the same
                        // path still authenticates.
                        .requestMatchers(HttpMethod.GET, "/api/stock/*").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/stock/availability").permitAll()

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

    /**
     * Maps a shortage to 409 rather than letting it reach the catch-all as a 500.
     *
     * <p>Ordered ahead of {@code GlobalExceptionHandler}, whose
     * {@code @ExceptionHandler(Exception.class)} would otherwise swallow it — the same
     * shadowing that turned authorization denials into 500s before
     * {@code SecurityExceptionHandler} was added.
     *
     * <p>Logged nowhere near ERROR: running out of stock is expected traffic, and the
     * saga's job is to cancel the order, not to raise an incident.
     */
    @RestControllerAdvice
    @Order(Ordered.HIGHEST_PRECEDENCE + 10)
    static class InsufficientStockHandler {

        @ExceptionHandler(InsufficientStockException.class)
        ResponseEntity<ApiError> handle(InsufficientStockException ex, HttpServletRequest request) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(
                    ApiError.of(409, "Conflict", ex.getMessage(),
                            request.getRequestURI(), CorrelationIdFilter.current(request)));
        }
    }
}
