package com.ecommerce.catalog.api;

import com.ecommerce.catalog.api.dto.CategoryRequest;
import com.ecommerce.catalog.api.dto.CategoryResponse;
import com.ecommerce.catalog.service.CatalogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;
import java.util.UUID;

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

    // ------------------------------------------------------------------- admin

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Create a category")
    public ResponseEntity<CategoryResponse> create(@Valid @RequestBody CategoryRequest request,
                                                   UriComponentsBuilder uriBuilder) {
        CategoryResponse created = catalog.createCategory(request);
        return ResponseEntity
                .created(uriBuilder.path("/api/categories/{id}").buildAndExpand(created.id()).toUri())
                .body(created);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Rename a category. The slug is immutable")
    public CategoryResponse update(@PathVariable UUID id, @Valid @RequestBody CategoryRequest request) {
        return catalog.updateCategory(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Delete an empty category. 409 if products still reference it")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        catalog.deleteCategory(id);
        return ResponseEntity.noContent().build();
    }
}
