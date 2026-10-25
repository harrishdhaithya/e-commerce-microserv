package com.ecommerce.inventory.api.dto;

import com.ecommerce.inventory.domain.StockItem;

import java.time.Instant;

/** The full picture, for administrators only. */
public record StockAdminResponse(
        String sku,
        int quantityOnHand,
        int quantityReserved,
        int available,
        int reorderLevel,
        boolean belowReorderLevel,
        Instant updatedAt) {

    public static StockAdminResponse from(StockItem item) {
        return new StockAdminResponse(
                item.getSku(),
                item.getQuantityOnHand(),
                item.getQuantityReserved(),
                item.available(),
                item.getReorderLevel(),
                item.isBelowReorderLevel(),
                item.getUpdatedAt());
    }
}
