package com.ecommerce.inventory.api.dto;

import com.ecommerce.inventory.domain.StockItem;

/**
 * Public availability.
 *
 * <p>Deliberately no exact counts: a competitor can read your public API, and
 * "available: 12" already tells a shopper everything they need. The admin view
 * ({@link StockAdminResponse}) has the real numbers.
 */
public record StockResponse(String sku, int available, boolean inStock) {

    public static StockResponse from(StockItem item) {
        return new StockResponse(item.getSku(), item.available(), item.available() > 0);
    }
}
