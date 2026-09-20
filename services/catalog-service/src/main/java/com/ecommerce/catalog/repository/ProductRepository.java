package com.ecommerce.catalog.repository;

import com.ecommerce.catalog.domain.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface ProductRepository extends JpaRepository<Product, Long> {

    /**
     * Listing query. Both filters are optional - a null argument disables that
     * predicate, which keeps one query serving every combination.
     *
     * <p>{@code join fetch} loads the category in the same statement. Without it,
     * rendering 20 products would fire 20 extra queries for their categories (the
     * N+1 problem), since the association is LAZY.
     *
     * <p>Phase 1 replaces the LIKE with something better; it is plenty for a demo
     * catalog and it is honest about what H2 can do (PLAN.md section 4).
     */
    @Query("""
            select p from Product p
              join fetch p.category c
            where p.active = true
              and (:query is null or lower(p.name) like lower(concat('%', :query, '%')))
              and (:categorySlug is null or c.slug = :categorySlug)
            """)
    Page<Product> findActive(@Param("query") String query,
                             @Param("categorySlug") String categorySlug,
                             Pageable pageable);

    @Query("select p from Product p join fetch p.category where p.publicId = :publicId")
    Optional<Product> findByPublicId(@Param("publicId") UUID publicId);

    Optional<Product> findBySku(String sku);
}
