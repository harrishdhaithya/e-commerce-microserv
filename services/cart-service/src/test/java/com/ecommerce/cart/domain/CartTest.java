package com.ecommerce.cart.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The aggregate's rules, tested without Spring.
 *
 * <p>Pure functions over in-memory state, so there is no reason to start a context.
 * They run in milliseconds and pin the behaviour the service layer relies on.
 */
class CartTest {

    private static final Duration TTL = Duration.ofDays(1);

    private static Cart cart() {
        return Cart.forCustomer("sub-alice", TTL);
    }

    private static void add(Cart cart, String productId, String price, int qty) {
        cart.addItem(productId, "SKU-" + productId, "Product " + productId,
                new BigDecimal(price), null, qty);
    }

    @Test
    void addingTheSameProductTwiceMergesRatherThanDuplicating() {
        Cart cart = cart();
        add(cart, "p1", "10.00", 2);
        add(cart, "p1", "10.00", 3);

        // One line, not two - which is what the unique (cart_id, product_id)
        // constraint enforces at the database level.
        assertEquals(1, cart.getItems().size());
        assertEquals(5, cart.getItems().get(0).getQuantity());
    }

    @Test
    void mergingTakesTheNewerPriceSnapshot() {
        Cart cart = cart();
        add(cart, "p1", "10.00", 1);
        add(cart, "p1", "12.00", 1);

        // The shopper just saw 12.00 on the product page; showing them the older
        // snapshot would be confusing.
        assertEquals(new BigDecimal("12.00"), cart.getItems().get(0).getUnitPrice());
    }

    @Test
    void subtotalAndCountAreComputedFromLines() {
        Cart cart = cart();
        add(cart, "p1", "10.00", 2);
        add(cart, "p2", "5.50", 3);

        assertEquals(5, cart.itemCount());
        assertEquals(new BigDecimal("36.50"), cart.subtotal());
    }

    @Test
    void emptyCartSubtotalIsZeroNotNull() {
        assertEquals(BigDecimal.ZERO, cart().subtotal());
        assertEquals(0, cart().itemCount());
    }

    @Test
    void settingQuantityToZeroRemovesTheLine() {
        Cart cart = cart();
        add(cart, "p1", "10.00", 2);

        cart.setItemQuantity("p1", 0);

        assertTrue(cart.getItems().isEmpty());
    }

    @Test
    void mergeFromSumsQuantitiesAndEmptiesTheSource() {
        Cart target = cart();
        add(target, "p1", "10.00", 1);
        add(target, "p2", "5.00", 1);

        Cart source = Cart.forAnonymous("anon-token", TTL);
        add(source, "p1", "10.00", 2);
        add(source, "p3", "7.00", 1);

        target.mergeFrom(source);

        assertEquals(3, target.getItems().size());
        assertEquals(3, target.findItem("p1").orElseThrow().getQuantity());
        assertTrue(source.getItems().isEmpty(), "the anonymous cart must be emptied");
    }

    @Test
    void claimingForACustomerClearsTheAnonymousToken() {
        Cart anonymous = Cart.forAnonymous("anon-token", TTL);

        anonymous.claimFor("sub-alice");

        assertEquals("sub-alice", anonymous.getCustomerId());
        // The CHECK constraint allows exactly one owner, so the token must go.
        assertNull(anonymous.getAnonymousToken());
    }

    @Test
    void expiringDistinguishesAbandonedFromEmpty() {
        Cart withItems = cart();
        add(withItems, "p1", "10.00", 1);
        withItems.markExpired();
        // Worth chasing with an email.
        assertEquals(CartStatus.ABANDONED, withItems.getStatus());

        Cart empty = cart();
        empty.markExpired();
        // Nothing was lost, so nothing to chase.
        assertEquals(CartStatus.EXPIRED, empty.getStatus());
    }

    @Test
    void checkedOutCartIsNoLongerActive() {
        Cart cart = cart();
        assertTrue(cart.isActive());

        cart.markCheckedOut();

        assertFalse(cart.isActive());
    }

    @Test
    void removingAnAbsentProductReportsNoChange() {
        assertFalse(cart().removeItem("nope"));
    }
}
