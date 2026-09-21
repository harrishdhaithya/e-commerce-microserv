package com.ecommerce.cart.domain;

/**
 * A cart's lifecycle.
 *
 * <p>An enum rather than a loose string, so an impossible value cannot be persisted.
 * Stored as its name (EnumType.STRING) - never the ordinal, which would silently
 * reassign every row the moment a constant is inserted into the middle of this list.
 */
public enum CartStatus {

    /** Being filled. The only status a shopper can modify. */
    ACTIVE,

    /** Checkout completed; order-service has taken what it needs. */
    CHECKED_OUT,

    /** Had items, then went untouched past its expiry. */
    ABANDONED,

    /** Expired while empty - nothing was lost, so nothing is worth chasing. */
    EXPIRED
}
