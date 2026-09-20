package com.ecommerce.catalog.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Create or replace a category. Admin only.
 *
 * <p>The slug pattern is enforced rather than derived from the name, because a slug
 * appears in URLs and in the storefront's category filter. Generating it silently
 * would mean renaming a category changes its URL and breaks every existing link.
 * Making it explicit forces that to be a decision.
 */
public record CategoryRequest(
        @NotBlank @Size(max = 120) String name,

        @NotBlank
        @Size(max = 140)
        @Pattern(
                regexp = "^[a-z0-9]+(?:-[a-z0-9]+)*$",
                message = "must be lowercase words separated by single hyphens")
        String slug) {
}
