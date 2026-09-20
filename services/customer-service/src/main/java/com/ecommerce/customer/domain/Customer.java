package com.ecommerce.customer.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * A customer's profile, as this platform sees them.
 *
 * <p>Keycloak owns identity: credentials, roles, tokens and sessions all live there,
 * and nothing in this class touches them. What lives here is domain data - the things
 * an e-commerce system needs that an identity provider has no opinion about.
 *
 * <p>Rows are created by just-in-time provisioning: the first time a valid token
 * arrives bearing an unknown {@code sub}, a customer is created from its claims.
 */
@Entity
@Table(name = "customers")
public class Customer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Stored as VARCHAR(36) rather than a native UUID column: Oracle has no UUID
     * type, so the explicit JdbcTypeCode keeps the mapping portable and keeps
     * Hibernate's schema validation happy.
     */
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "public_id", nullable = false, updatable = false, length = 36)
    private UUID publicId;

    /**
     * Keycloak's {@code sub} claim.
     *
     * <p>A String, not a UUID. OIDC defines {@code sub} as an opaque identifier;
     * Keycloak's happens to be UUID-shaped today, but user federation or a different
     * provider can return something else, and parsing a value you never need to
     * interpret only creates a way to fail.
     */
    @Column(name = "keycloak_id", nullable = false, updatable = false, length = 36)
    private String keycloakId;

    /**
     * Denormalized from the token's {@code email} claim, for display and admin
     * search. Never a key - Keycloak lets users change their email, and
     * {@link #keycloakId} is what stays stable.
     */
    @Column(nullable = false, length = 320)
    private String email;

    @Column(name = "first_name", length = 100)
    private String firstName;

    @Column(name = "last_name", length = 100)
    private String lastName;

    @Column(length = 40)
    private String phone;

    @Column(name = "marketing_opt_in", nullable = false)
    private boolean marketingOptIn = false;

    @Column(name = "loyalty_tier", nullable = false, length = 20)
    private String loyaltyTier = "STANDARD";

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /**
     * Optimistic locking. The annotation is what does the work - without it this
     * would be an ordinary integer column and concurrent updates would silently
     * overwrite each other.
     */
    @Version
    @Column(nullable = false)
    private int version;

    protected Customer() {
        // Required by JPA.
    }

    public Customer(String keycloakId, String email) {
        this.keycloakId = keycloakId;
        this.email = email;
    }

    /** Everything a token gives you on first sight of a customer. */
    public static Customer fromClaims(String keycloakId, String email,
                                      String firstName, String lastName) {
        Customer customer = new Customer(keycloakId, email);
        customer.firstName = firstName;
        customer.lastName = lastName;
        return customer;
    }

    @PrePersist
    void onCreate() {
        if (publicId == null) {
            publicId = UUID.randomUUID();
        }
        // Both set on insert: updated_at is NOT NULL, so it cannot wait for the
        // first update.
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    /**
     * Refreshes the fields Keycloak is authoritative for. Called on each
     * authenticated request so a name or email changed in the account console does
     * not leave this copy stale.
     */
    public void syncFromClaims(String email, String firstName, String lastName) {
        if (email != null) {
            this.email = email;
        }
        if (firstName != null) {
            this.firstName = firstName;
        }
        if (lastName != null) {
            this.lastName = lastName;
        }
    }

    public Long getId() {
        return id;
    }

    public UUID getPublicId() {
        return publicId;
    }

    public String getKeycloakId() {
        return keycloakId;
    }

    public String getEmail() {
        return email;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public boolean isMarketingOptIn() {
        return marketingOptIn;
    }

    public void setMarketingOptIn(boolean marketingOptIn) {
        this.marketingOptIn = marketingOptIn;
    }

    public String getLoyaltyTier() {
        return loyaltyTier;
    }

    public void setLoyaltyTier(String loyaltyTier) {
        this.loyaltyTier = loyaltyTier;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public int getVersion() {
        return version;
    }
}
