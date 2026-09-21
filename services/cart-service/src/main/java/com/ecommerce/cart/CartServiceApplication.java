package com.ecommerce.cart;

import com.ecommerce.cart.config.CartProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Active shopping carts.
 *
 * <p>{@code @EnableScheduling} is here for the expiry sweep. Redis was dropped from
 * this project (PLAN.md section 2), so the TTL its keys would have given us for free
 * is a scheduled job instead.
 */
@SpringBootApplication
@EnableConfigurationProperties(CartProperties.class)
@EnableScheduling
public class CartServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(CartServiceApplication.class, args);
    }
}
