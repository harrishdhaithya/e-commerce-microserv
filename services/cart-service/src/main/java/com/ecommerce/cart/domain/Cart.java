package com.ecommerce.cart.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * A shopper's cart: short-lived, mutable, and the one place in this platform where
 * "eventually correct" is genuinely acceptable.
 *
 * <p>Unlike {@code Address} in customer-service, this aggregate <em>does</em> own its
 * children. Every operation is "give me the cart, then change a line", so a
 * collection with cascade and orphan removal is the natural shape - and it lets the
 * add/merge invariants live here instead of leaking into the service layer.
 */
@Entity
@Table(name = "carts")
public class Cart {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "public_id", nullable = false, updatable = false, length = 36)
    private UUID publicId;

    /** Keycloak's {@code sub}. Null while the shopper is anonymous. */
    @Column(name = "customer_id", length = 36)
    private String customerId;

    /** Opaque token identifying an anonymous browser. Null once claimed by a customer. */
    @Column(name = "anonymous_token", length = 64)
    private String anonymousToken;

    /**
     * Stored as the enum NAME, never the ordinal. An ordinal would silently reassign
     * every existing row the moment a constant were inserted into the middle of
     * {@link CartStatus}.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CartStatus status = CartStatus.ACTIVE;

    @Column(nullable = false, length = 3)
    private String currency = "USD";

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Version
    @Column(nullable = false)
    private int version;

    /**
     * Cascade plus orphan removal, so emptying the list actually deletes rows. The
     * migration also declares ON DELETE CASCADE - belt and braces, because Hibernate
     * cascades only reach entities it knows about, and a bulk delete would bypass it.
     */
    @OneToMany(mappedBy = "cart", cascade = CascadeType.ALL, orphanRemoval = true)
    private final List<CartItem> items = new ArrayList<>();

    protected Cart() {
        // Required by JPA.
    }

    private Cart(String customerId, String anonymousToken, Duration ttl) {
        this.customerId = customerId;
        this.anonymousToken = anonymousToken;
        this.expiresAt = Instant.now().plus(ttl);
    }

    public static Cart forCustomer(String customerId, Duration ttl) {
        return new Cart(customerId, null, ttl);
    }

    public static Cart forAnonymous(String anonymousToken, Duration ttl) {
        return new Cart(null, anonymousToken, ttl);
    }

    @PrePersist
    void onCreate() {
        if (publicId == null) {
            publicId = UUID.randomUUID();
        }
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    // ------------------------------------------------------------------ items

    /**
     * Adds a product, or increases the quantity if it is already present.
     *
     * <p>Merging rather than appending is what the unique constraint on
     * {@code (cart_id, product_id)} enforces at the database level; doing it here
     * means the constraint never has to fire.
     */
    public CartItem addItem(String productId, String sku, String productName,
                            BigDecimal unitPrice, String imageUrl, int quantity) {
        Optional<CartItem> existing = findItem(productId);
        if (existing.isPresent()) {
            CartItem item = existing.get();
            item.increaseQuantityBy(quantity);
            // Take the newer snapshot: the shopper just saw this price on the
            // product page, so showing them the older one would be confusing.
            item.refreshSnapshot(productName, unitPrice, imageUrl);
            touch();
            return item;
        }

        CartItem item = new CartItem(this, productId, sku, productName, unitPrice, imageUrl, quantity);
        items.add(item);
        touch();
        return item;
    }

    /** Sets an absolute quantity. Zero or less removes the line. */
    public void setItemQuantity(String productId, int quantity) {
        Optional<CartItem> existing = findItem(productId);
        if (existing.isEmpty()) {
            return;
        }
        if (quantity <= 0) {
            items.remove(existing.get());
        } else {
            existing.get().setQuantity(quantity);
        }
        touch();
    }

    public boolean removeItem(String productId) {
        boolean removed = items.removeIf(item -> item.getProductId().equals(productId));
        if (removed) {
            touch();
        }
        return removed;
    }

    public void clear() {
        items.clear();
        touch();
    }

    public Optional<CartItem> findItem(String productId) {
        return items.stream().filter(item -> item.getProductId().equals(productId)).findFirst();
    }

    /**
     * Moves every line from another cart into this one.
     *
     * <p>Used when an anonymous shopper signs in: their basket must not vanish.
     * Quantities are summed per product, which is why this goes through
     * {@link #addItem} rather than copying lines across.
     */
    public void mergeFrom(Cart other) {
        for (CartItem item : List.copyOf(other.items)) {
            addItem(item.getProductId(), item.getSku(), item.getProductName(),
                    item.getUnitPrice(), item.getImageUrl(), item.getQuantity());
        }
        other.clear();
    }

    // ---------------------------------------------------------------- totals

    /** Computed on read, never stored - a stored subtotal can disagree with its lines. */
    public BigDecimal subtotal() {
        return items.stream()
                .map(CartItem::lineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public int itemCount() {
        return items.stream().mapToInt(CartItem::getQuantity).sum();
    }

    // ------------------------------------------------------------ lifecycle

    /** Pushes expiry out and marks the cart touched. Any change counts as activity. */
    public void extendExpiry(Duration ttl) {
        this.expiresAt = Instant.now().plus(ttl);
    }

    private void touch() {
        this.updatedAt = Instant.now();
    }

    public void claimFor(String customerId) {
        this.customerId = customerId;
        this.anonymousToken = null;
    }

    public void markCheckedOut() {
        this.status = CartStatus.CHECKED_OUT;
    }

    /**
     * An expired cart with items was abandoned; an empty one is simply gone.
     *
     * <p>The distinction exists so a later abandoned-cart email has something
     * meaningful to target - there is no point chasing an empty basket.
     */
    public void markExpired() {
        this.status = items.isEmpty() ? CartStatus.EXPIRED : CartStatus.ABANDONED;
    }

    public boolean isActive() {
        return status == CartStatus.ACTIVE;
    }

    public Long getId() {
        return id;
    }

    public UUID getPublicId() {
        return publicId;
    }

    public String getCustomerId() {
        return customerId;
    }

    public String getAnonymousToken() {
        return anonymousToken;
    }

    public CartStatus getStatus() {
        return status;
    }

    public String getCurrency() {
        return currency;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public List<CartItem> getItems() {
        return List.copyOf(items);
    }

    public int getVersion() {
        return version;
    }
}
