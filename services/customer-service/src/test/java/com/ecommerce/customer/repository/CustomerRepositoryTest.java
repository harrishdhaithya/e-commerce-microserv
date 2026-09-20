package com.ecommerce.customer.repository;

import com.ecommerce.customer.domain.Customer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
class CustomerRepositoryTest {

    @Autowired
    private CustomerRepository customers;

    @Autowired
    private AddressRepository addresses;

    @BeforeEach
    void setUp() {
        // Addresses first: they hold the foreign key.
        addresses.deleteAll();
        customers.deleteAll();
    }

    @Test
    void findsByKeycloakId() {
        customers.save(Customer.fromClaims("sub-alice", "alice@test.local", "Alice", "Smith"));

        var found = customers.findByKeycloakId("sub-alice");

        assertTrue(found.isPresent());
        assertEquals("alice@test.local", found.get().getEmail());
    }

    @Test
    void returnsEmptyForUnknownKeycloakId() {
        assertTrue(customers.findByKeycloakId("sub-nobody").isEmpty());
    }

    @Test
    void assignsPublicIdAndTimestampsOnInsert() {
        Customer saved = customers.save(Customer.fromClaims("sub-bob", "bob@test.local", "Bob", null));

        // @PrePersist fills these in; created_at and updated_at are both NOT NULL, so
        // an insert would fail outright if it did not.
        assertTrue(saved.getPublicId() != null);
        assertTrue(saved.getCreatedAt() != null);
        assertTrue(saved.getUpdatedAt() != null);
        assertEquals("STANDARD", saved.getLoyaltyTier());
    }

    @Test
    void findsByPublicId() {
        Customer saved = customers.save(Customer.fromClaims("sub-carol", "carol@test.local", null, null));

        assertTrue(customers.findByPublicId(saved.getPublicId()).isPresent());
        assertTrue(customers.findByPublicId(UUID.randomUUID()).isEmpty());
    }

    @Test
    void rejectsDuplicateKeycloakId() {
        customers.save(Customer.fromClaims("sub-dup", "first@test.local", null, null));

        // The constraint that makes just-in-time provisioning safe: two concurrent
        // first requests cannot both create a customer.
        assertThrows(DataIntegrityViolationException.class, () ->
                customers.saveAndFlush(Customer.fromClaims("sub-dup", "second@test.local", null, null)));
    }

    @Test
    void searchesEmailCaseInsensitively() {
        customers.save(Customer.fromClaims("sub-1", "Dave.Jones@Test.Local", null, null));
        customers.save(Customer.fromClaims("sub-2", "erin@test.local", null, null));

        var page = customers.findByEmailContainingIgnoreCase("JONES", PageRequest.of(0, 10));

        assertEquals(1, page.getTotalElements());
        assertEquals("Dave.Jones@Test.Local", page.getContent().get(0).getEmail());
    }
}
