package com.ecommerce.cart.api.dto;

import com.ecommerce.cart.domain.CartItem;

import java.math.BigDecimal;

public record CartItemResponse(
        String productId,
        String sku,
        String productName,
        BigDecimal unitPrice,
        int quantity,
        BigDecimal lineTotal,
        String imageUrl) {

    public static CartItemResponse from(CartItem item) {
        return new CartItemResponse(
                item.getProductId(),
                item.getSku(),
                item.getProductName(),
                item.getUnitPrice(),
                item.getQuantity(),
                item.lineTotal(),
                item.getImageUrl());
    }
}
