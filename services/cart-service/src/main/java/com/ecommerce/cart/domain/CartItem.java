package com.ecommerce.cart.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * One line in a cart.
 *
 * <p>{@code productName}, {@code unitPrice} and {@code imageUrl} are snapshots taken
 * when the product was added, so rendering a cart needs no call to catalog-service.
 * They are <em>not</em> trusted at checkout - {@code POST /api/cart/validate}
 * re-reads the catalog and reports anything that moved. Storing nothing would mean a
 * catalog call per line on every render; trusting the snapshot forever would let a
 * shopper hold a stale price indefinitely.
 */
@Entity
@Table(name = "cart_items")
public class CartItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cart_id", nullable = false, updatable = false)
    private Cart cart;

    /**
     * The product's public id, as a plain string.
     *
     * <p>No foreign key and no type-safety across the boundary: products live in
     * catalog-service's database, which this service cannot reach. That is the
     * database-per-service rule doing its job rather than a shortcut.
     */
    @Column(name = "product_id", nullable = false, updatable = false, length = 36)
    private String productId;

    @Column(nullable = false, length = 64)
    private String sku;

    @Column(name = "product_name", nullable = false, length = 200)
    private String productName;

    @Column(name = "unit_price", nullable = false, precision = 19, scale = 4)
    private BigDecimal unitPrice;

    @Column(name = "image_url", length = 500)
    private String imageUrl;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "added_at", nullable = false, updatable = false)
    private Instant addedAt;

    protected CartItem() {
        // Required by JPA.
    }

    CartItem(Cart cart, String productId, String sku, String productName,
             BigDecimal unitPrice, String imageUrl, int quantity) {
        this.cart = cart;
        this.productId = productId;
        this.sku = sku;
        this.productName = productName;
        this.unitPrice = unitPrice;
        this.imageUrl = imageUrl;
        this.quantity = quantity;
    }

    @PrePersist
    void onCreate() {
        if (addedAt == null) {
            addedAt = Instant.now();
        }
    }

    /** Line total, computed rather than stored - a stored copy is a thing that can disagree. */
    public BigDecimal lineTotal() {
        return unitPrice.multiply(BigDecimal.valueOf(quantity));
    }

    void increaseQuantityBy(int amount) {
        this.quantity += amount;
    }

    void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    /** Refreshes the display snapshot after a validate found the catalog had moved. */
    void refreshSnapshot(String productName, BigDecimal unitPrice, String imageUrl) {
        this.productName = productName;
        this.unitPrice = unitPrice;
        this.imageUrl = imageUrl;
    }

    public Long getId() {
        return id;
    }

    public Cart getCart() {
        return cart;
    }

    public String getProductId() {
        return productId;
    }

    public String getSku() {
        return sku;
    }

    public String getProductName() {
        return productName;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public int getQuantity() {
        return quantity;
    }

    public Instant getAddedAt() {
        return addedAt;
    }
}
