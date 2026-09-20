package com.ecommerce.catalog.service;

import com.ecommerce.catalog.api.dto.CategoryResponse;
import com.ecommerce.catalog.api.dto.PagedResponse;
import com.ecommerce.catalog.api.dto.ProductResponse;
import com.ecommerce.catalog.domain.Product;
import com.ecommerce.catalog.repository.CategoryRepository;
import com.ecommerce.catalog.repository.ProductRepository;
import com.ecommerce.common.web.ResourceNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class CatalogService {

    private final ProductRepository products;
    private final CategoryRepository categories;

    public CatalogService(ProductRepository products, CategoryRepository categories) {
        this.products = products;
        this.categories = categories;
    }

    public PagedResponse<ProductResponse> listProducts(String query, String categorySlug, Pageable pageable) {
        // Blank query params arrive as "" from the browser; normalize so the
        // repository's null check actually disables the predicate.
        Page<Product> page = products.findActive(
                blankToNull(query), blankToNull(categorySlug), pageable);
        return PagedResponse.from(page, ProductResponse::from);
    }

    public ProductResponse getProduct(UUID publicId) {
        return products.findByPublicId(publicId)
                .map(ProductResponse::from)
                .orElseThrow(() -> ResourceNotFoundException.of("Product", publicId));
    }

    public List<CategoryResponse> listCategories() {
        return categories.findAllByOrderByNameAsc().stream()
                .map(CategoryResponse::from)
                .toList();
    }

    private static String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }
}
