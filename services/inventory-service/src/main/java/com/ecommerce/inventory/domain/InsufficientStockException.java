package com.ecommerce.inventory.domain;

/**
 * Not enough stock available to satisfy a reservation.
 *
 * <p><strong>A normal business outcome, not a defect.</strong> The saga handles it by
 * cancelling the order, so it maps to a 409 and is logged at INFO — logging it as an
 * error would bury real faults among routine out-of-stock events.
 */
public class InsufficientStockException extends RuntimeException {

    private final String sku;
    private final int requested;
    private final int available;

    public InsufficientStockException(String sku, int requested, int available) {
        super("Insufficient stock for %s: requested %d, available %d"
                .formatted(sku, requested, available));
        this.sku = sku;
        this.requested = requested;
        this.available = available;
    }

    public String getSku() {
        return sku;
    }

    public int getRequested() {
        return requested;
    }

    public int getAvailable() {
        return available;
    }
}
