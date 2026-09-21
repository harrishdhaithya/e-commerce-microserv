package com.ecommerce.cart.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Tunables, bound from the {@code cart.*} block in application.yml.
 *
 * <p>Typed configuration rather than scattered {@code @Value} annotations, so the
 * lifetimes and the catalog endpoint are discoverable in one place and validated at
 * startup rather than on first use.
 *
 * @param ttlSignedIn  how long a signed-in customer's cart survives without being touched
 * @param ttlAnonymous the same for an anonymous cart - deliberately shorter, since it
 *                     belongs to a browser rather than a person
 */
@ConfigurationProperties(prefix = "cart")
public record CartProperties(
        Duration ttlSignedIn,
        Duration ttlAnonymous,
        String catalogBaseUrl,
        Duration catalogTimeout) {
}
