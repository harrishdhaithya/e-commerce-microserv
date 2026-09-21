package com.ecommerce.cart.api.dto;

import com.ecommerce.cart.domain.Cart;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * A cart as the client sees it.
 *
 * <p>{@code itemCount} and {@code subtotal} are computed from the lines on every
 * read, never stored. A stored total is one more thing that can disagree with the
 * data it summarises.
 */
public record CartResponse(
        UUID id,
        String currency,
        List<CartItemResponse> items,
        int itemCount,
        BigDecimal subtotal,
        Instant expiresAt) {

    public static CartResponse from(Cart cart) {
        return new CartResponse(
                cart.getPublicId(),
                cart.getCurrency(),
                cart.getItems().stream().map(CartItemResponse::from).toList(),
                cart.itemCount(),
                cart.subtotal(),
                cart.getExpiresAt());
    }
}
