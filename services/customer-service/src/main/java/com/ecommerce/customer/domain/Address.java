package com.ecommerce.customer.domain;

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
import jakarta.persistence.Version;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * A shipping or billing address belonging to one customer.
 *
 * <p>Note what orders do with these: {@code order-service} <em>copies</em> the
 * address values onto the order at checkout rather than referencing this row. An
 * order records what was agreed at a point in time, so editing an address next month
 * must not rewrite last month's orders. That is also why deleting an address here is
 * safe - no order depends on it.
 */
@Entity
@Table(name = "addresses")
public class Address {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "public_id", nullable = false, updatable = false, length = 36)
    private UUID publicId;

    /**
     * Unidirectional: an Address knows its Customer, but Customer has no address
     * collection.
     *
     * <p>Every operation the API needs - list, fetch, update, delete - is a
     * repository query scoped by customer, so a collection would add the classic
     * bidirectional synchronisation bug without buying anything. It also keeps
     * loading a Customer from ever dragging in their addresses.
     *
     * <p>LAZY because the common case is reading an address and never touching the
     * customer behind it.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false, updatable = false)
    private Customer customer;

    /** Free-text nickname: "Home", "Office". */
    @Column(length = 60)
    private String label;

    @Column(name = "recipient_name", nullable = false, length = 200)
    private String recipientName;

    @Column(nullable = false, length = 200)
    private String line1;

    @Column(length = 200)
    private String line2;

    @Column(nullable = false, length = 120)
    private String city;

    /** State, province or county. Optional because not every country has one. */
    @Column(length = 120)
    private String region;

    @Column(name = "postal_code", nullable = false, length = 20)
    private String postalCode;

    /** ISO 3166-1 alpha-2, uppercase. */
    @Column(name = "country_code", nullable = false, length = 2)
    private String countryCode;

    @Column(length = 40)
    private String phone;

    /**
     * At most one address per customer should carry each of these. That is an
     * invariant the service layer enforces when setting a new default - the database
     * cannot express "unique where true" portably.
     */
    @Column(name = "is_default_shipping", nullable = false)
    private boolean defaultShipping = false;

    @Column(name = "is_default_billing", nullable = false)
    private boolean defaultBilling = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Version
    @Column(nullable = false)
    private int version;

    protected Address() {
        // Required by JPA.
    }

    public Address(Customer customer, String recipientName, String line1, String city,
                   String postalCode, String countryCode) {
        this.customer = customer;
        this.recipientName = recipientName;
        this.line1 = line1;
        this.city = city;
        this.postalCode = postalCode;
        this.countryCode = normalizeCountryCode(countryCode);
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

    private static String normalizeCountryCode(String countryCode) {
        return countryCode == null ? null : countryCode.trim().toUpperCase();
    }

    public Long getId() {
        return id;
    }

    public UUID getPublicId() {
        return publicId;
    }

    public Customer getCustomer() {
        return customer;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public String getRecipientName() {
        return recipientName;
    }

    public void setRecipientName(String recipientName) {
        this.recipientName = recipientName;
    }

    public String getLine1() {
        return line1;
    }

    public void setLine1(String line1) {
        this.line1 = line1;
    }

    public String getLine2() {
        return line2;
    }

    public void setLine2(String line2) {
        this.line2 = line2;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
    }

    public String getPostalCode() {
        return postalCode;
    }

    public void setPostalCode(String postalCode) {
        this.postalCode = postalCode;
    }

    public String getCountryCode() {
        return countryCode;
    }

    public void setCountryCode(String countryCode) {
        this.countryCode = normalizeCountryCode(countryCode);
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public boolean isDefaultShipping() {
        return defaultShipping;
    }

    public void setDefaultShipping(boolean defaultShipping) {
        this.defaultShipping = defaultShipping;
    }

    public boolean isDefaultBilling() {
        return defaultBilling;
    }

    public void setDefaultBilling(boolean defaultBilling) {
        this.defaultBilling = defaultBilling;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public int getVersion() {
        return version;
    }
}
