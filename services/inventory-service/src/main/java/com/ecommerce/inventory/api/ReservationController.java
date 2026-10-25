package com.ecommerce.inventory.api;

import com.ecommerce.inventory.api.dto.ReservationResponse;
import com.ecommerce.inventory.api.dto.ReserveRequest;
import com.ecommerce.inventory.service.StockReservationCoordinator;
import com.ecommerce.inventory.service.StockService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.UUID;

/**
 * Stock reservations.
 *
 * <p>A resource in its own right rather than a sub-resource of stock — reservations
 * are created by the checkout saga, and conflating them with stock levels would make
 * the gateway route for one imply the other.
 *
 * <p><strong>These endpoints must not be routed publicly.</strong> They are called by
 * {@code order-service}, not by browsers: a client able to reserve stock directly
 * could hold inventory without ever placing an order. Every method requires
 * {@code ADMIN} until Phase 3 introduces proper service-to-service credentials.
 */
@RestController
@RequestMapping("/api/reservations")
@Tag(name = "Reservations", description = "Stock held for in-flight orders")
public class ReservationController {

    private final StockReservationCoordinator coordinator;
    private final StockService stock;

    public ReservationController(StockReservationCoordinator coordinator, StockService stock) {
        this.coordinator = coordinator;
        this.stock = stock;
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Hold stock for an order. Retries on optimistic-lock conflict")
    public ResponseEntity<ReservationResponse> reserve(@Valid @RequestBody ReserveRequest request,
                                                       UriComponentsBuilder uriBuilder) {
        ReservationResponse created = ReservationResponse.from(
                coordinator.reserve(request.orderId(), request.sku(), request.quantity()));

        return ResponseEntity
                .created(uriBuilder.path("/api/reservations/{id}").buildAndExpand(created.id()).toUri())
                .body(created);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Reservation state")
    public ReservationResponse get(@PathVariable UUID id) {
        return ReservationResponse.from(stock.requireReservation(id));
    }

    @PostMapping("/{id}/commit")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Payment succeeded: the held stock leaves inventory")
    public ReservationResponse commit(@PathVariable UUID id) {
        return ReservationResponse.from(coordinator.commit(id));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Release the hold: stock returns to availability")
    public ReservationResponse release(@PathVariable UUID id) {
        return ReservationResponse.from(coordinator.release(id));
    }
}
