package com.ecommerce.inventory.repository;

import com.ecommerce.inventory.domain.StockItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface StockItemRepository extends JpaRepository<StockItem, Long> {

    Optional<StockItem> findBySku(String sku);

    /** Batch availability, so a cart page is one call rather than one call per line. */
    List<StockItem> findBySkuIn(Collection<String> skus);

    boolean existsBySku(String sku);

    /**
     * Items at or below their reorder level.
     *
     * <p>Expressed against computed availability rather than a stored flag, so it
     * cannot go stale.
     */
    @Query("""
            select s from StockItem s
             where (s.quantityOnHand - s.quantityReserved) <= s.reorderLevel
             order by s.sku asc
            """)
    List<StockItem> findBelowReorderLevel();
}
