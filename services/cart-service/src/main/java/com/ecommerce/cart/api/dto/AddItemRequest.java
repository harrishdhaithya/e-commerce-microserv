package com.ecommerce.cart.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

/**
 * Add a product to the cart.
 *
 * <p>Only the product id and a quantity. Name and price are deliberately absent:
 * taking them from the client would let a caller set their own prices. The service
 * reads both from catalog-service.
 */
public record AddItemRequest(
        @NotBlank String productId,

        // A ceiling as well as a floor. Without the max, a fat-fingered 99999 becomes
        // an order nobody can fulfil.
        @Min(1) @Max(99) int quantity) {

    public int quantityOrOne() {
        return quantity <= 0 ? 1 : quantity;
    }
}
