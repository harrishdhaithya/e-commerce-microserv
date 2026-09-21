package com.ecommerce.cart.service;

import com.ecommerce.cart.config.CartProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * Reads product details from catalog-service.
 *
 * <p>The first cross-service call in this project, and a deliberately synchronous one:
 * a shopper adding to their cart needs the name and price now, and a failure means
 * "could not add" rather than lost work. That is exactly the read path PLAN.md section
 * 1 reserves REST for.
 *
 * <p>Two things this class must get right:
 *
 * <ul>
 *   <li><strong>A timeout on every call.</strong> An untimed call is how one slow
 *       service exhausts another's threads and takes the whole platform down with it.
 *       Configured via {@code cart.catalog-timeout}, default 2s.</li>
 *   <li><strong>Distinguish "no such product" from "catalog is down".</strong> A 404
 *       is a client error the shopper caused; anything else is an outage, and
 *       reporting the two identically makes both undiagnosable.</li>
 * </ul>
 *
 * <p>A circuit breaker belongs here too, and arrives with Resilience4j in Phase 3.
 * Until then a timeout is the only thing standing between a slow catalog and a
 * hanging cart.
 */
@Component
public class CatalogClient {

    private static final Logger log = LoggerFactory.getLogger(CatalogClient.class);

    private final RestClient restClient;

    public CatalogClient(RestTemplateBuilder builder, CartProperties properties) {
        this.restClient = RestClient.builder()
                .baseUrl(properties.catalogBaseUrl())
                .requestFactory(builder
                        .connectTimeout(properties.catalogTimeout())
                        .readTimeout(properties.catalogTimeout())
                        .buildRequestFactory())
                .build();
    }

    /**
     * Fetches a product, or empty if the catalog says it does not exist.
     *
     * @throws CatalogUnavailableException if the catalog could not be reached, which
     *                                    is a different problem from a missing product
     */
    public Optional<CatalogProduct> findProduct(String productId) {
        try {
            CatalogProduct product = restClient.get()
                    .uri("/api/products/{id}", productId)
                    .retrieve()
                    // 404 means the product is gone - a normal answer, not a fault.
                    .onStatus(HttpStatusCode::is4xxClientError, (request, response) -> {
                        // Swallow so the body below returns null rather than throwing.
                    })
                    .body(CatalogProduct.class);

            return Optional.ofNullable(product);
        } catch (RestClientException e) {
            // Timeout, connection refused, unparseable response: the catalog is not
            // answering. Never report this as "product not found".
            log.warn("catalog-service unreachable while fetching product {}: {}",
                    productId, e.getMessage());
            throw new CatalogUnavailableException(
                    "Could not reach the product catalog. Please try again.", e);
        }
    }

    /**
     * The slice of a product this service needs.
     *
     * <p>Mirrors catalog-service's {@code ProductResponse} but only the fields used
     * here. Ignoring the rest means a field added there does not break deserialization
     * - Jackson's default is to fail on unknown properties, but Boot disables that.
     */
    public record CatalogProduct(
            String id,
            String sku,
            String name,
            BigDecimal price,
            String imageUrl) {
    }

    /** The catalog could not be reached. Distinct from a product not existing. */
    public static class CatalogUnavailableException extends RuntimeException {
        public CatalogUnavailableException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
