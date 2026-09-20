package com.ecommerce.catalog.service;

import com.ecommerce.catalog.api.dto.CategoryRequest;
import com.ecommerce.catalog.api.dto.CategoryResponse;
import com.ecommerce.catalog.api.dto.ProductRequest;
import com.ecommerce.catalog.api.dto.ProductResponse;
import com.ecommerce.catalog.domain.Category;
import com.ecommerce.catalog.domain.Product;
import com.ecommerce.catalog.repository.CategoryRepository;
import com.ecommerce.catalog.repository.ProductRepository;
import com.ecommerce.common.web.ConflictException;
import com.ecommerce.common.web.PagedResponse;
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

    // ------------------------------------------------------------------ writes
    //
    // Admin only. The role check lives on the controller methods, where the operation
    // is - this layer assumes the caller has already been authorised.

    @Transactional
    public ProductResponse createProduct(ProductRequest request) {
        if (products.existsBySku(request.sku())) {
            throw ConflictException.duplicate("Product", "SKU", request.sku());
        }

        Product product = new Product(
                request.sku(), request.name(), request.description(), request.price(),
                requireCategory(request.categorySlug()), request.imageUrl());
        product.setActive(request.active() == null || request.active());

        return ProductResponse.from(products.save(product));
    }

    @Transactional
    public ProductResponse updateProduct(UUID publicId, ProductRequest request) {
        Product product = products.findByPublicId(publicId)
                .orElseThrow(() -> ResourceNotFoundException.of("Product", publicId));

        // The SKU is a business key: it ends up on labels, in order lines, and in
        // other services' records. Letting an edit change it would silently break
        // those references, so it is immutable once created.
        if (!product.getSku().equals(request.sku())) {
            throw new ConflictException(
                    "SKU cannot be changed (create a new product instead); expected '%s'"
                            .formatted(product.getSku()));
        }

        product.setName(request.name());
        product.setDescription(request.description());
        product.setPrice(request.price());
        product.setCategory(requireCategory(request.categorySlug()));
        product.setImageUrl(request.imageUrl());
        if (request.active() != null) {
            product.setActive(request.active());
        }

        return ProductResponse.from(products.save(product));
    }

    /**
     * Discontinues a product rather than deleting the row.
     *
     * <p>A hard delete would be safe as far as other services go - order-service
     * copies product details onto the order - but the row is still wanted here so an
     * order history page can resolve what was bought. Setting {@code active = false}
     * removes it from browsing while keeping it addressable by id.
     */
    @Transactional
    public void discontinueProduct(UUID publicId) {
        Product product = products.findByPublicId(publicId)
                .orElseThrow(() -> ResourceNotFoundException.of("Product", publicId));
        product.setActive(false);
        products.save(product);
    }

    @Transactional
    public CategoryResponse createCategory(CategoryRequest request) {
        if (categories.existsBySlug(request.slug())) {
            throw ConflictException.duplicate("Category", "slug", request.slug());
        }
        return CategoryResponse.from(categories.save(new Category(request.name(), request.slug())));
    }

    @Transactional
    public CategoryResponse updateCategory(UUID publicId, CategoryRequest request) {
        Category category = categories.findByPublicId(publicId)
                .orElseThrow(() -> ResourceNotFoundException.of("Category", publicId));

        // Same reasoning as the SKU: the slug is in every category URL and in the
        // storefront's filter. Changing it would 404 every existing link.
        if (!category.getSlug().equals(request.slug())) {
            throw new ConflictException(
                    "Slug cannot be changed (it appears in URLs); expected '%s'"
                            .formatted(category.getSlug()));
        }

        category.setName(request.name());
        return CategoryResponse.from(categories.save(category));
    }

    @Transactional
    public void deleteCategory(UUID publicId) {
        Category category = categories.findByPublicId(publicId)
                .orElseThrow(() -> ResourceNotFoundException.of("Category", publicId));

        long inUse = products.countByCategory_Slug(category.getSlug());
        if (inUse > 0) {
            throw ConflictException.inUse("Category", category.getSlug(),
                    "%d product(s) still reference it".formatted(inUse));
        }

        categories.delete(category);
    }

    private Category requireCategory(String slug) {
        return categories.findBySlug(slug)
                .orElseThrow(() -> ResourceNotFoundException.of("Category", slug));
    }

    private static String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }
}
