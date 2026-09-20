package com.ecommerce.catalog.api.dto;

import com.ecommerce.catalog.domain.Product;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * What the API returns for a product.
 *
 * <p>A DTO rather than the entity, for three reasons: the internal {@code id} and
 * {@code version} stay private, the JSON shape stops changing every time the schema
 * does, and serializing a JPA entity with a LAZY association would trip Hibernate
 * into loading it (or failing) during response writing.
 */
public record ProductResponse(
        UUID id,
        String sku,
        String name,
        String description,
        BigDecimal price,
        String categoryName,
        String categorySlug,
        String imageUrl) {

    public static ProductResponse from(Product product) {
        return new ProductResponse(
                product.getPublicId(),
                product.getSku(),
                product.getName(),
                product.getDescription(),
                product.getPrice(),
                product.getCategory().getName(),
                product.getCategory().getSlug(),
                product.getImageUrl());
    }
}
