package com.ecommerce.inventory.service;

import com.ecommerce.common.web.ConflictException;
import com.ecommerce.common.web.ResourceNotFoundException;
import com.ecommerce.inventory.config.InventoryProperties;
import com.ecommerce.inventory.domain.LedgerReason;
import com.ecommerce.inventory.domain.ReservationStatus;
import com.ecommerce.inventory.domain.StockItem;
import com.ecommerce.inventory.domain.StockLedgerEntry;
import com.ecommerce.inventory.domain.StockReservation;
import com.ecommerce.inventory.repository.StockItemRepository;
import com.ecommerce.inventory.repository.StockLedgerRepository;
import com.ecommerce.inventory.repository.StockReservationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Stock operations, each in its own transaction.
 *
 * <p><strong>Every method here is transactional and none of them retry.</strong> That
 * division is deliberate: a retry has to start a <em>new</em> transaction with a fresh
 * read, because a transaction that has taken an optimistic-lock failure is marked
 * rollback-only and cannot be reused. The retry therefore lives in
 * {@link StockReservationCoordinator}, a separate bean — separate because a
 * self-invocation would bypass Spring's proxy and silently run without a transaction
 * at all.
 *
 * <p>Note also that {@code reserve} touches three tables: it updates the counters,
 * inserts a reservation and appends a ledger row. "It's a single-table update" is not
 * why these are transactional — but it would not be true here anyway.
 */
@Service
public class StockService {

    private static final Logger log = LoggerFactory.getLogger(StockService.class);

    private final StockItemRepository stockItems;
    private final StockReservationRepository reservations;
    private final StockLedgerRepository ledger;
    private final InventoryProperties properties;

    public StockService(StockItemRepository stockItems,
                        StockReservationRepository reservations,
                        StockLedgerRepository ledger,
                        InventoryProperties properties) {
        this.stockItems = stockItems;
        this.reservations = reservations;
        this.ledger = ledger;
        this.properties = properties;
    }

    // ------------------------------------------------------------------ reads

    @Transactional(readOnly = true)
    public StockItem requireItem(String sku) {
        return stockItems.findBySku(sku)
                .orElseThrow(() -> ResourceNotFoundException.of("Stock item", sku));
    }

    @Transactional(readOnly = true)
    public List<StockItem> findAll(List<String> skus) {
        return stockItems.findBySkuIn(skus);
    }

    @Transactional(readOnly = true)
    public List<StockItem> findBelowReorderLevel() {
        return stockItems.findBelowReorderLevel();
    }

    // ------------------------------------------------------------ reservation

    /**
     * Holds stock for an order.
     *
     * <p>Not retried here — see the class comment. A caller that gets
     * {@code OptimisticLockingFailureException} from this method is expected to call
     * it again, which is what the coordinator does.
     */
    @Transactional
    public StockReservation reserve(String orderId, String sku, int quantity) {
        StockItem item = requireItem(sku);

        // Throws InsufficientStockException if availability is short. The version
        // check on flush is what makes that decision safe under concurrency: a writer
        // working from a stale read updates zero rows and fails instead.
        item.reserve(quantity);

        StockReservation reservation =
                new StockReservation(orderId, sku, quantity, properties.reservationTtl());
        reservations.save(reservation);
        stockItems.save(item);

        ledger.save(new StockLedgerEntry(sku, -quantity, LedgerReason.RESERVE,
                reservation.getPublicId().toString()));

        return reservation;
    }

    /**
     * Payment succeeded: the held quantity leaves inventory for good.
     *
     * <p>Availability does not change — the stock was already unavailable while held.
     * Only {@code quantity_on_hand} moves.
     */
    @Transactional
    public StockReservation commit(UUID reservationId) {
        StockReservation reservation = requireHeld(reservationId);
        StockItem item = requireItem(reservation.getSku());

        item.commitReservation(reservation.getQuantity());
        reservation.transitionTo(ReservationStatus.COMMITTED);

        stockItems.save(item);
        reservations.save(reservation);
        ledger.save(new StockLedgerEntry(reservation.getSku(), -reservation.getQuantity(),
                LedgerReason.COMMIT, reservationId.toString()));

        return reservation;
    }

    /** Payment failed or the order was cancelled: the stock goes back on sale. */
    @Transactional
    public StockReservation release(UUID reservationId) {
        StockReservation reservation = requireHeld(reservationId);
        StockItem item = requireItem(reservation.getSku());

        item.releaseReservation(reservation.getQuantity());
        reservation.transitionTo(ReservationStatus.RELEASED);

        stockItems.save(item);
        reservations.save(reservation);
        ledger.save(new StockLedgerEntry(reservation.getSku(), reservation.getQuantity(),
                LedgerReason.RELEASE, reservationId.toString()));

        return reservation;
    }

    /**
     * Returns stock from a reservation nobody finished with.
     *
     * <p>Separate from {@link #release} only so the ledger records {@code EXPIRY}
     * rather than {@code RELEASE} — an abandoned checkout and a failed payment are
     * different stories and the audit trail should say which.
     */
    @Transactional
    public void expire(StockReservation reservation) {
        StockItem item = requireItem(reservation.getSku());

        item.releaseReservation(reservation.getQuantity());
        reservation.transitionTo(ReservationStatus.EXPIRED);

        stockItems.save(item);
        reservations.save(reservation);
        ledger.save(new StockLedgerEntry(reservation.getSku(), reservation.getQuantity(),
                LedgerReason.EXPIRY, reservation.getPublicId().toString()));

        log.info("Expired reservation {} for order {}, returned {} x {}",
                reservation.getPublicId(), reservation.getOrderId(),
                reservation.getQuantity(), reservation.getSku());
    }

    @Transactional(readOnly = true)
    public StockReservation requireReservation(UUID reservationId) {
        return reservations.findByPublicId(reservationId)
                .orElseThrow(() -> ResourceNotFoundException.of("Reservation", reservationId));
    }

    // ----------------------------------------------------------------- admin

    @Transactional
    public StockItem upsert(String sku, int onHand, int reorderLevel) {
        StockItem item = stockItems.findBySku(sku).orElse(null);

        if (item == null) {
            item = stockItems.save(new StockItem(sku, onHand, reorderLevel));
            ledger.save(new StockLedgerEntry(sku, onHand, LedgerReason.RECEIPT, null));
            return item;
        }

        int delta = item.setOnHand(onHand);
        item.setReorderLevel(reorderLevel);
        stockItems.save(item);
        if (delta != 0) {
            ledger.save(new StockLedgerEntry(sku, delta, LedgerReason.ADJUSTMENT, null));
        }
        return item;
    }

    @Transactional
    public StockItem adjust(String sku, int delta, LedgerReason reason) {
        if (delta == 0) {
            throw new ConflictException("An adjustment of zero changes nothing");
        }
        StockItem item = requireItem(sku);
        item.adjustBy(delta);
        stockItems.save(item);
        ledger.save(new StockLedgerEntry(sku, delta, reason, null));
        return item;
    }

    private StockReservation requireHeld(UUID reservationId) {
        StockReservation reservation = requireReservation(reservationId);
        if (!reservation.isHeld()) {
            // Already committed, released or expired. Guarded rather than ignored,
            // because silently re-applying would double-count the stock.
            throw new ConflictException(
                    "Reservation %s is already %s".formatted(reservationId, reservation.getStatus()));
        }
        return reservation;
    }
}
