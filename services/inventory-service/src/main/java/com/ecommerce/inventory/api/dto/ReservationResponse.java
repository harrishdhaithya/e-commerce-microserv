package com.ecommerce.inventory.api.dto;

import com.ecommerce.inventory.domain.ReservationStatus;
import com.ecommerce.inventory.domain.StockReservation;

import java.time.Instant;
import java.util.UUID;

public record ReservationResponse(
        UUID id,
        String orderId,
        String sku,
        int quantity,
        ReservationStatus status,
        Instant expiresAt) {

    public static ReservationResponse from(StockReservation reservation) {
        return new ReservationResponse(
                reservation.getPublicId(),
                reservation.getOrderId(),
                reservation.getSku(),
                reservation.getQuantity(),
                reservation.getStatus(),
                reservation.getExpiresAt());
    }
}
