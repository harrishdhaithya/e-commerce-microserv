package com.ecommerce.inventory.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * A quantity of one SKU held for one order.
 *
 * <p>Separate rows rather than a counter, because the saga needs to commit or release
 * a <em>specific</em> hold, and because the ledger needs something to reference.
 */
@Entity
@Table(name = "stock_reservations")
public class StockReservation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "public_id", nullable = false, updatable = false, length = 36)
    private UUID publicId;

    /** The order this is held for. A plain string — orders live in order-service. */
    @Column(name = "order_id", nullable = false, updatable = false, length = 36)
    private String orderId;

    @Column(nullable = false, updatable = false, length = 64)
    private String sku;

    @Column(nullable = false, updatable = false)
    private int quantity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReservationStatus status = ReservationStatus.HELD;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Version
    @Column(nullable = false)
    private int version;

    protected StockReservation() {
        // Required by JPA.
    }

    public StockReservation(String orderId, String sku, int quantity, Duration ttl) {
        this.orderId = orderId;
        this.sku = sku;
        this.quantity = quantity;
        this.expiresAt = Instant.now().plus(ttl);
    }

    @PrePersist
    void onCreate() {
        if (publicId == null) {
            publicId = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    /**
     * Moves to a terminal status.
     *
     * <p>Guarded rather than a plain setter: committing an already-released
     * reservation would double-count the stock, and Kafka's at-least-once delivery
     * makes a repeated instruction a certainty in Phase 3 rather than a possibility.
     */
    public void transitionTo(ReservationStatus next) {
        if (status != ReservationStatus.HELD) {
            throw new IllegalStateException(
                    "Reservation %s is already %s and cannot become %s"
                            .formatted(publicId, status, next));
        }
        this.status = next;
    }

    public boolean isHeld() {
        return status == ReservationStatus.HELD;
    }

    public Long getId() {
        return id;
    }

    public UUID getPublicId() {
        return publicId;
    }

    public String getOrderId() {
        return orderId;
    }

    public String getSku() {
        return sku;
    }

    public int getQuantity() {
        return quantity;
    }

    public ReservationStatus getStatus() {
        return status;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public int getVersion() {
        return version;
    }
}
