package com.ecommerce.catalog.api.dto;

import com.ecommerce.catalog.domain.Category;

import java.util.UUID;

public record CategoryResponse(UUID id, String name, String slug) {

    public static CategoryResponse from(Category category) {
        return new CategoryResponse(category.getPublicId(), category.getName(), category.getSlug());
    }
}
