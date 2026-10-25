package com.ecommerce.inventory.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Hold stock for an order. Called by the saga, not by clients. */
public record ReserveRequest(
        @NotBlank @Size(max = 36) String orderId,
        @NotBlank @Size(max = 64) String sku,
        @Min(1) @Max(999) int quantity) {
}
