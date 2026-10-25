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

import java.time.Instant;

/**
 * One movement of stock. Append-only: there are no setters and nothing updates these
 * rows.
 *
 * <p>The ledger is what makes a counter auditable. Current quantity is derivable from
 * the sum of changes, so if {@code stock_items} ever drifts, this is how you find out
 * when and why — and how you repair it.
 */
@Entity
@Table(name = "stock_ledger")
public class StockLedgerEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, updatable = false, length = 64)
    private String sku;

    /** Signed. Negative removes from circulation, positive returns it. */
    @Column(nullable = false, updatable = false)
    private int change;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 30)
    private LedgerReason reason;

    /** The reservation or order that caused this, where there is one. */
    @Column(updatable = false, length = 36)
    private String reference;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    protected StockLedgerEntry() {
        // Required by JPA.
    }

    public StockLedgerEntry(String sku, int change, LedgerReason reason, String reference) {
        this.sku = sku;
        this.change = change;
        this.reason = reason;
        this.reference = reference;
    }

    @PrePersist
    void onCreate() {
        if (occurredAt == null) {
            occurredAt = Instant.now();
        }
    }

    public Long getId() {
        return id;
    }

    public String getSku() {
        return sku;
    }

    public int getChange() {
        return change;
    }

    public LedgerReason getReason() {
        return reason;
    }

    public String getReference() {
        return reference;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }
}
