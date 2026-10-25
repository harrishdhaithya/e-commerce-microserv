package com.ecommerce.inventory.domain;

/**
 * A reservation's lifecycle. Stored as the enum NAME, never the ordinal.
 *
 * <p>Only {@link #HELD} holds stock out of circulation. The other three are terminal
 * and have already returned it, one way or another.
 */
public enum ReservationStatus {

    /** Stock is held for an order that has not yet completed. */
    HELD,

    /** Payment succeeded; the stock has left inventory for good. */
    COMMITTED,

    /** Payment failed or the order was cancelled; stock returned to available. */
    RELEASED,

    /** Nobody finished the checkout in time; the sweep returned the stock. */
    EXPIRED
}
