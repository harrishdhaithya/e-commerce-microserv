package com.ecommerce.inventory.service;

import com.ecommerce.common.web.ConflictException;
import com.ecommerce.inventory.domain.InsufficientStockException;
import com.ecommerce.inventory.domain.StockItem;
import com.ecommerce.inventory.repository.StockItemRepository;
import com.ecommerce.inventory.repository.StockLedgerRepository;
import com.ecommerce.inventory.repository.StockReservationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The test this service exists to pass.
 *
 * <p>Ten threads try to reserve the last item at the same moment. Exactly one must
 * succeed, stock must never go negative, and the ledger must agree with the counters.
 *
 * <p>Three things make this a real race rather than a sequential loop wearing a
 * costume, and getting any of them wrong makes the test pass for the wrong reason:
 *
 * <ul>
 *   <li><strong>The test method is not {@code @Transactional}.</strong> If it were,
 *       every thread would share one transaction and there would be no conflict to
 *       detect — the test would pass even against the naive implementation.</li>
 *   <li><strong>Each thread calls through the Spring bean</strong>, so every attempt
 *       gets its own transaction and its own read.</li>
 *   <li><strong>A latch lines the threads up.</strong> Without it they trickle in one
 *       at a time and never actually collide.</li>
 * </ul>
 */
@SpringBootTest
@ActiveProfiles("test")
class StockConcurrencyTest {

    private static final String SKU = "RACE-0001";
    private static final int THREADS = 10;

    @Autowired
    private StockReservationCoordinator coordinator;

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
    }

    private record Outcome(int succeeded, int insufficient, int exhausted) {
    }

    /** Fires THREADS concurrent reservations at one SKU and tallies what happened. */
    private Outcome race(int stockOnHand, int quantityEach) throws Exception {
        stockItems.save(new StockItem(SKU, stockOnHand, 0));

        AtomicInteger succeeded = new AtomicInteger();
        AtomicInteger insufficient = new AtomicInteger();
        AtomicInteger exhausted = new AtomicInteger();

        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch finished = new CountDownLatch(THREADS);

        try (ExecutorService pool = Executors.newFixedThreadPool(THREADS)) {
            for (int i = 0; i < THREADS; i++) {
                String orderId = "order-" + i;
                pool.submit(() -> {
                    try {
                        startGate.await();
                        coordinator.reserve(orderId, SKU, quantityEach);
                        succeeded.incrementAndGet();
                    } catch (InsufficientStockException e) {
                        insufficient.incrementAndGet();
                    } catch (ConflictException e) {
                        // Retries exhausted under contention. A legitimate outcome, and
                        // deliberately distinct from "there was not enough stock".
                        exhausted.incrementAndGet();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        finished.countDown();
                    }
                });
            }

            startGate.countDown();
            assertTrue(finished.await(30, TimeUnit.SECONDS), "threads did not finish in time");
        }

        return new Outcome(succeeded.get(), insufficient.get(), exhausted.get());
    }

    /**
     * Repeated, because a concurrency test that passes once has told you very little.
     * Timing-dependent bugs hide behind a single green run.
     */
    @RepeatedTest(20)
    void exactlyOneThreadGetsTheLastItem() throws Exception {
        Outcome outcome = race(1, 1);

        assertEquals(1, outcome.succeeded(),
                "exactly one reservation may succeed against a stock of 1");
        assertEquals(THREADS - 1, outcome.insufficient() + outcome.exhausted(),
                "every other thread must fail, not silently oversell");

        StockItem item = stockItems.findBySku(SKU).orElseThrow();
        assertEquals(1, item.getQuantityReserved());
        assertEquals(1, item.getQuantityOnHand());
        assertEquals(0, item.available(), "nothing left to sell");
    }

    @Test
    void stockNeverGoesNegativeUnderContention() throws Exception {
        // Five available, ten threads wanting two each: at most two can win.
        Outcome outcome = race(5, 2);

        StockItem item = stockItems.findBySku(SKU).orElseThrow();
        assertTrue(item.available() >= 0, "available must never be negative");
        assertTrue(item.getQuantityReserved() <= item.getQuantityOnHand(),
                "reserved must never exceed on-hand");
        assertEquals(outcome.succeeded() * 2, item.getQuantityReserved(),
                "reserved must equal exactly what was granted");
    }

    @Test
    void theLedgerAgreesWithTheCounters() throws Exception {
        race(3, 1);

        StockItem item = stockItems.findBySku(SKU).orElseThrow();
        int reserveEntries = (int) ledger.findAll().stream()
                .filter(e -> e.getSku().equals(SKU) && e.getChange() < 0)
                .count();

        // One RESERVE row per successful hold, and no rows for the losers. If a failed
        // attempt left a ledger entry behind, the transaction boundary is wrong.
        assertEquals(item.getQuantityReserved(), reserveEntries,
                "the ledger must record exactly the holds that succeeded");
    }

    @Test
    void versionIncrementsOncePerSuccessfulReservation() throws Exception {
        Outcome outcome = race(4, 1);

        StockItem item = stockItems.findBySku(SKU).orElseThrow();
        // The version is the evidence that losers did not write. Each successful
        // reserve bumps it exactly once; a lost race bumps nothing.
        assertEquals(outcome.succeeded(), item.getVersion(),
                "only successful writes may advance the version");
    }
}
