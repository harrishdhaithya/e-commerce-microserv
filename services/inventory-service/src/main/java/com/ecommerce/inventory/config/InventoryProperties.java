package com.ecommerce.inventory.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Tunables read from Java.
 *
 * <p>Only one, deliberately. {@code inventory.max-reserve-attempts} and
 * {@code inventory.retry-backoff-ms} are also configured in application.yml, but
 * {@code @Retryable} and {@code @Backoff} resolve them as property placeholders
 * directly — they never pass through this record. Declaring them here as well would
 * be worse than omitting them: the fields would look authoritative while nothing read
 * them, so changing one and expecting behaviour to follow would be a reasonable
 * mistake to make.
 *
 * <p>(With the hand-written retry loop this record held all three, which was arguably
 * tidier — see StockReservationCoordinator for that trade-off.)
 *
 * @param reservationTtl how long stock stays held before the expiry sweep releases it.
 *                       Long enough for a slow payment, short enough that an abandoned
 *                       checkout does not strand stock.
 */
@ConfigurationProperties(prefix = "inventory")
public record InventoryProperties(Duration reservationTtl) {
}
