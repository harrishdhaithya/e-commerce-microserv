package com.ecommerce.catalog.api;

import com.ecommerce.catalog.api.dto.CategoryResponse;
import com.ecommerce.catalog.service.CatalogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Categories.
 *
 * <p>A separate controller from products because they are separate resources, even
 * though one service owns both. Keeping one controller per resource is what stops
 * this class from becoming a catch-all as brands, variants and reviews arrive.
 */
@RestController
@RequestMapping("/api/categories")
@Tag(name = "Categories", description = "Product categories")
public class CategoryController {

    private final CatalogService catalog;

    public CategoryController(CatalogService catalog) {
        this.catalog = catalog;
    }

    @GetMapping
    @Operation(summary = "List all categories, ordered by name")
    public List<CategoryResponse> listCategories() {
        return catalog.listCategories();
    }
}
