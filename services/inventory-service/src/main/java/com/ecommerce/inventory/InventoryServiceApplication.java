package com.ecommerce.inventory;

import com.ecommerce.inventory.config.InventoryProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Stock levels and reservations.
 *
 * <p>Two opt-ins worth knowing about, because both fail silently if removed:
 *
 * <ul>
 *   <li>{@code @EnableScheduling} drives {@code ReservationExpirySweeper}, the backstop
 *       that returns stock from a checkout that died mid-flight.</li>
 *   <li>{@code @EnableRetry} registers the AOP machinery that makes {@code @Retryable}
 *       on {@code StockReservationCoordinator} do anything. Spring Boot does not
 *       auto-configure this — putting spring-retry on the classpath alone is not
 *       enough. Without it the annotations are inert metadata: the code compiles, the
 *       context starts, and no retry ever happens. {@code StockConcurrencyTest} is the
 *       only thing that catches its absence — removing it puts the overselling bug
 *       straight back.</li>
 * </ul>
 */
@SpringBootApplication
@EnableConfigurationProperties(InventoryProperties.class)
@EnableScheduling
@EnableRetry
public class InventoryServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(InventoryServiceApplication.class, args);
    }
}
