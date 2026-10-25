package com.ecommerce.inventory.repository;

import com.ecommerce.inventory.domain.StockLedgerEntry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StockLedgerRepository extends JpaRepository<StockLedgerEntry, Long> {

    /** Movement history for one SKU, newest first. Paginated — this table only grows. */
    Page<StockLedgerEntry> findBySkuOrderByOccurredAtDesc(String sku, Pageable pageable);
}
