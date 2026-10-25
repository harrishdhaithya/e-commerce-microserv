package com.ecommerce.inventory.service;

import com.ecommerce.inventory.domain.ReservationStatus;
import com.ecommerce.inventory.domain.StockReservation;
import com.ecommerce.inventory.repository.StockReservationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/**
 * Releases stock from reservations nobody finished with.
 *
 * <p><strong>The backstop for a saga that dies mid-flight.</strong> Without this, one
 * crashed checkout holds its stock forever and the inventory becomes permanently
 * unsellable — a slow leak rather than a visible failure, which is worse.
 *
 * <p>Each reservation is expired in its own transaction rather than one big batch, so
 * a single conflicting row cannot roll back the rest of the sweep.
 */
@Component
public class ReservationExpirySweeper {

    private static final Logger log = LoggerFactory.getLogger(ReservationExpirySweeper.class);
    private static final int BATCH_SIZE = 200;

    private final StockReservationRepository reservations;
    private final StockService stockService;

    public ReservationExpirySweeper(StockReservationRepository reservations, StockService stockService) {
        this.reservations = reservations;
        this.stockService = stockService;
    }

    @Scheduled(initialDelay = 30_000, fixedDelay = 60_000)
    public void sweep() {
        int released = sweepOnce();
        if (released > 0) {
            log.info("Expiry sweep released {} reservation(s)", released);
        }
    }

    /** Visible for tests, which cannot wait a minute for the scheduler. */
    public int sweepOnce() {
        List<StockReservation> expired = reservations
                .findByStatusAndExpiresAtBeforeOrderByExpiresAtAsc(
                        ReservationStatus.HELD , Instant.now(), PageRequest.of(0, BATCH_SIZE));

        int released = 0;
        for (StockReservation reservation : expired) {
            try {
                stockService.expire(reservation);
                released++;
            } catch (OptimisticLockingFailureException | IllegalStateException e) {
                // Something committed or released this reservation while the sweep was
                // looking at it. That is the correct outcome, not an error - the next
                // run will pick up anything genuinely still stuck.
                log.debug("Skipped reservation {} during sweep: {}",
                        reservation.getPublicId(), e.getMessage());
            }
        }
        return released;
    }
}
