package com.ecommerce.customer.repository;

import com.ecommerce.customer.domain.Address;
import com.ecommerce.customer.domain.Customer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
class AddressRepositoryTest {

    @Autowired
    private AddressRepository addresses;

    @Autowired
    private CustomerRepository customers;

    private Customer alice;
    private Customer bob;
    private Address aliceHome;

    @BeforeEach
    void setUp() {
        addresses.deleteAll();
        customers.deleteAll();

        alice = customers.save(Customer.fromClaims("sub-alice", "alice@test.local", "Alice", "Smith"));
        bob = customers.save(Customer.fromClaims("sub-bob", "bob@test.local", "Bob", "Jones"));

        aliceHome = addresses.save(address(alice, "Alice Smith", "1 Alice Street"));
        addresses.save(address(alice, "Alice Smith", "2 Second Avenue"));
        addresses.save(address(bob, "Bob Jones", "9 Bob Lane"));
    }

    private static Address address(Customer owner, String recipient, String line1) {
        return new Address(owner, recipient, line1, "London", "SW1A 1AA", "gb");
    }

    @Test
    void listsOnlyTheOwnersAddresses() {
        List<Address> found = addresses.findByCustomer_KeycloakIdOrderByCreatedAtAsc("sub-alice");

        assertEquals(2, found.size());
        assertTrue(found.stream().allMatch(a -> a.getRecipientName().equals("Alice Smith")));
    }

    @Test
    void findsOwnAddressByPublicId() {
        var found = addresses.findByPublicIdAndCustomer_KeycloakId(aliceHome.getPublicId(), "sub-alice");

        assertTrue(found.isPresent());
        assertEquals("1 Alice Street", found.get().getLine1());
    }

    @Test
    void doesNotFindAnotherCustomersAddress() {
        // The test that matters. Bob asks for Alice's address by its real id and gets
        // nothing - so the service returns 404 without ever having to check ownership
        // itself.
        var found = addresses.findByPublicIdAndCustomer_KeycloakId(aliceHome.getPublicId(), "sub-bob");

        assertTrue(found.isEmpty());
    }

    @Test
    void returnsEmptyForUnknownPublicId() {
        assertTrue(addresses.findByPublicIdAndCustomer_KeycloakId(UUID.randomUUID(), "sub-alice").isEmpty());
    }

    @Test
    void countsPerCustomer() {
        assertEquals(2, addresses.countByCustomer_KeycloakId("sub-alice"));
        assertEquals(1, addresses.countByCustomer_KeycloakId("sub-bob"));
        assertEquals(0, addresses.countByCustomer_KeycloakId("sub-nobody"));
    }

    @Test
    void normalizesCountryCodeToUppercase() {
        // Constructed with "gb" above.
        assertEquals("GB", aliceHome.getCountryCode());
    }

    @Test
    void clearsDefaultShippingForOneCustomerOnly() {
        aliceHome.setDefaultShipping(true);
        addresses.saveAndFlush(aliceHome);

        Address bobAddress = addresses.findByCustomer_KeycloakIdOrderByCreatedAtAsc("sub-bob").get(0);
        bobAddress.setDefaultShipping(true);
        addresses.saveAndFlush(bobAddress);

        int cleared = addresses.clearDefaultShipping("sub-alice");

        assertEquals(1, cleared);
        // Re-fetch rather than reusing the entities above: clearAutomatically emptied
        // the persistence context, so anything held before the bulk update is now
        // detached and still carries the stale flag.
        assertFalse(addresses.findByPublicIdAndCustomer_KeycloakId(
                aliceHome.getPublicId(), "sub-alice").orElseThrow().isDefaultShipping());
        assertTrue(addresses.findByPublicIdAndCustomer_KeycloakId(
                bobAddress.getPublicId(), "sub-bob").orElseThrow().isDefaultShipping());
    }

    @Test
    void clearingDefaultIsANoOpWhenNoneIsSet() {
        assertEquals(0, addresses.clearDefaultShipping("sub-alice"));
        assertEquals(0, addresses.clearDefaultBilling("sub-alice"));
    }

    @Test
    void shippingAndBillingDefaultsAreIndependent() {
        aliceHome.setDefaultShipping(true);
        aliceHome.setDefaultBilling(true);
        addresses.saveAndFlush(aliceHome);

        addresses.clearDefaultShipping("sub-alice");

        Address reloaded = addresses
                .findByPublicIdAndCustomer_KeycloakId(aliceHome.getPublicId(), "sub-alice")
                .orElseThrow();
        assertFalse(reloaded.isDefaultShipping());
        assertTrue(reloaded.isDefaultBilling());
    }
}
