package com.ecommerce.cart.repository;

/**
 * Who a cart belongs to: a signed-in customer, or an anonymous browser.
 *
 * <p>Carts are the one resource in this platform whose owner is not simply the
 * {@code sub} claim, because a shopper must be able to fill a basket before creating
 * an account. Modelling that as an explicit type keeps the ambiguity in one place
 * instead of spreading nullable {@code customerId} / {@code anonymousToken} pairs
 * through every method signature.
 */
public sealed interface CartOwner {

    record Customer(String subject) implements CartOwner {
    }

    record Anonymous(String token) implements CartOwner {
    }

    static CartOwner of(String subject, String anonymousToken) {
        if (subject != null && !subject.isBlank()) {
            return new Customer(subject);
        }
        if (anonymousToken != null && !anonymousToken.isBlank()) {
            return new Anonymous(anonymousToken);
        }
        throw new IllegalArgumentException(
                "A cart needs an owner: either an authenticated subject or an anonymous token");
    }
}
