package com.ecommerce.inventory.api.dto;

import jakarta.validation.constraints.Min;

/** Set absolute stock for a SKU. Admin only. */
public record StockUpsertRequest(
        @Min(0) int quantityOnHand,
        @Min(0) int reorderLevel) {
}
