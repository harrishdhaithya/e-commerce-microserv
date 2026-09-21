package com.ecommerce.cart.api.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Result of re-checking a cart against the live catalog before checkout.
 *
 * <p>The point is to tell the shopper precisely what changed, so the UI can say "the
 * price of X went from $179 to $189" instead of silently charging the new amount.
 * Silently accepting the difference is the behaviour this endpoint exists to prevent.
 */
public record CartValidationResponse(boolean valid, List<Issue> issues, CartResponse cart) {

    public record Issue(
            String productId,
            String productName,
            Type type,
            BigDecimal oldPrice,
            BigDecimal newPrice) {

        public enum Type {
            /** Snapshot price no longer matches the catalog. */
            PRICE_CHANGED,
            /** Product has been discontinued, or no longer exists. */
            UNAVAILABLE
        }

        public static Issue priceChanged(String productId, String name, BigDecimal from, BigDecimal to) {
            return new Issue(productId, name, Type.PRICE_CHANGED, from, to);
        }

        public static Issue unavailable(String productId, String name) {
            return new Issue(productId, name, Type.UNAVAILABLE, null, null);
        }
    }

    public static CartValidationResponse of(List<Issue> issues, CartResponse cart) {
        return new CartValidationResponse(issues.isEmpty(), issues, cart);
    }
}
