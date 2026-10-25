package com.ecommerce.inventory.repository;

import com.ecommerce.inventory.domain.ReservationStatus;
import com.ecommerce.inventory.domain.StockReservation;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StockReservationRepository extends JpaRepository<StockReservation, Long> {

    Optional<StockReservation> findByPublicId(UUID publicId);

    List<StockReservation> findByOrderId(String orderId);

    /**
     * Held reservations past their expiry, oldest first.
     *
     * <p>Paged rather than "find everything expired": a sweep that loads every stale
     * reservation at once is one that eventually runs out of memory instead of doing
     * its job.
     */
    List<StockReservation> findByStatusAndExpiresAtBeforeOrderByExpiresAtAsc(
            ReservationStatus status, Instant cutoff, Pageable pageable);

    long countByStatus(ReservationStatus status);
}
