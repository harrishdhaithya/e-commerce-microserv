package com.ecommerce.inventory.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;

/**
 * Stock for one SKU.
 *
 * <p>The interesting entity in this project. Two customers can reach it at the same
 * moment wanting the last item, and exactly one of them must win.
 *
 * <p>That is what {@link Version} does: Hibernate appends {@code WHERE version = ?} to
 * every update, so a writer working from a stale read updates zero rows and gets an
 * {@code OptimisticLockingFailureException} instead of silently overwriting the other
 * writer's change. The retry lives outside the transaction — see
 * {@code StockReservationCoordinator}.
 */
@Entity
@Table(name = "stock_items")
public class StockItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, updatable = false, length = 64)
    private String sku;

    /** Physically present, including anything currently held by a reservation. */
    @Column(name = "quantity_on_hand", nullable = false)
    private int quantityOnHand;

    /** Held for in-flight orders. Still on the shelf, but not sellable. */
    @Column(name = "quantity_reserved", nullable = false)
    private int quantityReserved;

    @Column(name = "reorder_level", nullable = false)
    private int reorderLevel;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private int version;

    protected StockItem() {
        // Required by JPA.
    }

    public StockItem(String sku, int quantityOnHand, int reorderLevel) {
        this.sku = sku;
        this.quantityOnHand = quantityOnHand;
        this.reorderLevel = reorderLevel;
    }

    @PrePersist
    @PreUpdate
    void touch() {
        updatedAt = Instant.now();
    }

    /**
     * What can still be sold.
     *
     * <p>Computed, never stored. A third counter would be a third thing that can
     * disagree with the other two, and reconciling it is work with no upside.
     */
    public int available() {
        return quantityOnHand - quantityReserved;
    }

    public boolean isBelowReorderLevel() {
        return available() <= reorderLevel;
    }

    /**
     * Holds stock for an order.
     *
     * <p>Reserving rather than decrementing is deliberate: a straight decrement at
     * checkout would strand stock on every abandoned cart. The quantity stays on hand
     * and simply stops being available.
     *
     * @throws InsufficientStockException if there is not enough available
     */
    public void reserve(int quantity) {
        requirePositive(quantity);
        if (available() < quantity) {
            throw new InsufficientStockException(sku, quantity, available());
        }
        quantityReserved += quantity;
    }

    /**
     * Turns a held quantity into a dispatched one.
     *
     * <p>Leaves {@code reserved} and {@code onHand} together, so availability does not
     * change — the stock was already unavailable while held.
     */
    public void commitReservation(int quantity) {
        requirePositive(quantity);
        if (quantityReserved < quantity) {
            throw new IllegalStateException(
                    "Cannot commit %d of %s: only %d reserved".formatted(quantity, sku, quantityReserved));
        }
        quantityReserved -= quantity;
        quantityOnHand -= quantity;
    }

    /** Returns a held quantity to availability. Nothing left the shelf. */
    public void releaseReservation(int quantity) {
        requirePositive(quantity);
        if (quantityReserved < quantity) {
            throw new IllegalStateException(
                    "Cannot release %d of %s: only %d reserved".formatted(quantity, sku, quantityReserved));
        }
        quantityReserved -= quantity;
    }

    /** Absolute set, by an administrator. Returns the signed difference for the ledger. */
    public int setOnHand(int newQuantity) {
        requireNotNegative(newQuantity);
        if (newQuantity < quantityReserved) {
            throw new IllegalStateException(
                    "Cannot set %s to %d: %d is currently reserved"
                            .formatted(sku, newQuantity, quantityReserved));
        }
        int delta = newQuantity - quantityOnHand;
        quantityOnHand = newQuantity;
        return delta;
    }

    /** Signed correction, by an administrator. */
    public void adjustBy(int delta) {
        int target = quantityOnHand + delta;
        if (target < quantityReserved) {
            throw new IllegalStateException(
                    "Adjusting %s by %d would drop below the %d currently reserved"
                            .formatted(sku, delta, quantityReserved));
        }
        requireNotNegative(target);
        quantityOnHand = target;
    }

    public void setReorderLevel(int reorderLevel) {
        requireNotNegative(reorderLevel);
        this.reorderLevel = reorderLevel;
    }

    private static void requirePositive(int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be positive, got " + quantity);
        }
    }

    private static void requireNotNegative(int quantity) {
        if (quantity < 0) {
            throw new IllegalArgumentException("Quantity must not be negative, got " + quantity);
        }
    }

    public Long getId() {
        return id;
    }

    public String getSku() {
        return sku;
    }

    public int getQuantityOnHand() {
        return quantityOnHand;
    }

    public int getQuantityReserved() {
        return quantityReserved;
    }

    public int getReorderLevel() {
        return reorderLevel;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public int getVersion() {
        return version;
    }
}
