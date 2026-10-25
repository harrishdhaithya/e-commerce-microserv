package com.ecommerce.inventory.service;

import com.ecommerce.common.web.ConflictException;
import com.ecommerce.inventory.domain.LedgerReason;
import com.ecommerce.inventory.domain.ReservationStatus;
import com.ecommerce.inventory.domain.StockItem;
import com.ecommerce.inventory.domain.StockReservation;
import com.ecommerce.inventory.repository.StockItemRepository;
import com.ecommerce.inventory.repository.StockLedgerRepository;
import com.ecommerce.inventory.repository.StockReservationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Reserve → commit, and reserve → release.
 *
 * <p>The counters are the thing to watch. Reserving moves stock out of availability
 * without taking it off the shelf; committing takes it off the shelf; releasing puts
 * it back on sale. Getting any of those three wrong leaks or duplicates inventory.
 */
@SpringBootTest
@ActiveProfiles("test")
class ReservationLifecycleTest {

    private static final String SKU = "LIFE-0001";

    @Autowired
    private StockService stock;

    @Autowired
    private StockReservationCoordinator coordinator;

    @Autowired
    private ReservationExpirySweeper sweeper;

    @Autowired
    private StockItemRepository stockItems;

    @Autowired
    private StockReservationRepository reservations;

    @Autowired
    private StockLedgerRepository ledger;

    @BeforeEach
    void setUp() {
        reservations.deleteAll();
        ledger.deleteAll();
        stockItems.deleteAll();
        stockItems.save(new StockItem(SKU, 10, 2));
    }

    private StockItem reload() {
        return stockItems.findBySku(SKU).orElseThrow();
    }

    @Test
    void reservingHoldsStockWithoutRemovingIt() {
        coordinator.reserve("order-1", SKU, 3);

        StockItem item = reload();
        // Still on the shelf...
        assertEquals(10, item.getQuantityOnHand());
        // ...but no longer sellable.
        assertEquals(3, item.getQuantityReserved());
        assertEquals(7, item.available());
    }

    @Test
    void committingTakesStockOffTheShelf() {
        StockReservation reservation = coordinator.reserve("order-1", SKU, 3);

        coordinator.commit(reservation.getPublicId());

        StockItem item = reload();
        assertEquals(7, item.getQuantityOnHand());
        assertEquals(0, item.getQuantityReserved());
        // Availability is unchanged by the commit: it was already unavailable.
        assertEquals(7, item.available());
    }

    @Test
    void releasingPutsStockBackOnSale() {
        StockReservation reservation = coordinator.reserve("order-1", SKU, 3);

        coordinator.release(reservation.getPublicId());

        StockItem item = reload();
        assertEquals(10, item.getQuantityOnHand());
        assertEquals(0, item.getQuantityReserved());
        assertEquals(10, item.available(), "a released hold leaves no trace in the counters");
    }

    @Test
    void everyMovementLeavesALedgerTrail() {
        StockReservation reservation = coordinator.reserve("order-1", SKU, 3);
        coordinator.commit(reservation.getPublicId());

        List<LedgerReason> reasons = ledger.findAll().stream()
                .filter(e -> e.getSku().equals(SKU))
                .map(e -> e.getReason())
                .toList();

        assertTrue(reasons.contains(LedgerReason.RESERVE));
        assertTrue(reasons.contains(LedgerReason.COMMIT));
    }

    @Test
    void aReservationCannotBeCommittedTwice() {
        StockReservation reservation = coordinator.reserve("order-1", SKU, 3);
        coordinator.commit(reservation.getPublicId());

        // Kafka's at-least-once delivery makes a repeated instruction a certainty in
        // Phase 3. Double-committing would take the stock off the shelf twice.
        assertThrows(ConflictException.class, () -> coordinator.commit(reservation.getPublicId()));

        assertEquals(7, reload().getQuantityOnHand(), "the second commit must change nothing");
    }

    @Test
    void aCommittedReservationCannotBeReleased() {
        StockReservation reservation = coordinator.reserve("order-1", SKU, 3);
        coordinator.commit(reservation.getPublicId());

        assertThrows(ConflictException.class, () -> coordinator.release(reservation.getPublicId()));
    }

    @Test
    void expirySweepReturnsStockFromAnAbandonedCheckout() throws Exception {
        StockReservation reservation = coordinator.reserve("order-1", SKU, 4);
        assertEquals(6, reload().available());

        // The test profile sets a 2s TTL precisely so this is observable.
        Thread.sleep(2_200);
        int released = sweeper.sweepOnce();

        assertEquals(1, released);
        assertEquals(10, reload().available(), "stock must be sellable again");
        assertEquals(ReservationStatus.EXPIRED,
                reservations.findByPublicId(reservation.getPublicId()).orElseThrow().getStatus());

        // EXPIRY, not RELEASE: an abandoned checkout and a failed payment are different
        // stories and the audit trail should say which.
        assertTrue(ledger.findAll().stream()
                .anyMatch(e -> e.getReason() == LedgerReason.EXPIRY));
    }

    @Test
    void sweepLeavesCommittedReservationsAlone() throws Exception {
        StockReservation reservation = coordinator.reserve("order-1", SKU, 4);
        coordinator.commit(reservation.getPublicId());

        Thread.sleep(2_200);

        assertEquals(0, sweeper.sweepOnce(), "only HELD reservations are swept");
        assertEquals(6, reload().getQuantityOnHand());
    }

    @Test
    void adminAdjustmentCannotDropBelowReservedQuantity() {
        coordinator.reserve("order-1", SKU, 8);

        // 10 on hand, 8 held. Dropping to 5 would leave reservations the stock cannot
        // honour, so the CHECK constraint's intent is enforced in the domain too.
        assertThrows(IllegalStateException.class, () -> stock.upsert(SKU, 5, 2));
    }

    @Test
    void adjustmentsAreRecordedWithTheirReason() {
        stock.adjust(SKU, -2, LedgerReason.ADJUSTMENT);

        assertEquals(8, reload().getQuantityOnHand());
        assertTrue(ledger.findAll().stream()
                .anyMatch(e -> e.getReason() == LedgerReason.ADJUSTMENT && e.getChange() == -2));
    }
}
