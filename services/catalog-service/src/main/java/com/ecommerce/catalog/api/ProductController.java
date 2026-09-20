package com.ecommerce.catalog.api;

import com.ecommerce.catalog.api.dto.PagedResponse;
import com.ecommerce.catalog.api.dto.ProductResponse;
import com.ecommerce.catalog.service.CatalogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Products.
 *
 * <p>Mapped to the resource path, not to the service: URLs name resources
 * ({@code /api/products}), never the service that happens to serve them. The service
 * topology is an implementation detail, so merging or splitting a service later is a
 * gateway routing change rather than a breaking change for every client. See
 * docs/architecture.md.
 */
@RestController
@RequestMapping("/api/products")
@Tag(name = "Products", description = "Product browsing and search")
public class ProductController {

    /** Hard ceiling on page size, so a client cannot ask for the whole table. */
    private static final int MAX_PAGE_SIZE = 100;

    private final CatalogService catalog;

    public ProductController(CatalogService catalog) {
        this.catalog = catalog;
    }

    @GetMapping
    @Operation(summary = "List active products, newest first by default")
    public PagedResponse<ProductResponse> listProducts(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(MAX_PAGE_SIZE) int size,
            @RequestParam(defaultValue = "createdAt,desc") String sort) {

        return catalog.listProducts(q, category, PageRequest.of(page, size, parseSort(sort)));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Fetch one product by its public id")
    public ProductResponse getProduct(@PathVariable UUID id) {
        return catalog.getProduct(id);
    }

    /**
     * Parses "field,direction" into a Sort, restricted to an allowlist.
     *
     * <p>Passing a raw client string into Sort.by lets a caller sort by any entity
     * property, which leaks schema details and can be used to probe the model. An
     * allowlist keeps the surface to what the API actually promises.
     */
    private static Sort parseSort(String sort) {
        String[] parts = sort.split(",", 2);
        String field = switch (parts[0].trim()) {
            case "name" -> "name";
            case "price" -> "price";
            case "createdAt" -> "createdAt";
            default -> throw new IllegalArgumentException(
                    "Unsupported sort field: " + parts[0] + " (allowed: name, price, createdAt)");
        };
        Sort.Direction direction = (parts.length > 1 && parts[1].trim().equalsIgnoreCase("asc"))
                ? Sort.Direction.ASC
                : Sort.Direction.DESC;
        return Sort.by(direction, field);
    }
}
