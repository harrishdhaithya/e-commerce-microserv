package com.ecommerce.inventory.api.dto;

import com.ecommerce.inventory.domain.LedgerReason;
import com.ecommerce.inventory.domain.StockLedgerEntry;

import java.time.Instant;

public record LedgerEntryResponse(
        String sku,
        int change,
        LedgerReason reason,
        String reference,
        Instant occurredAt) {

    public static LedgerEntryResponse from(StockLedgerEntry entry) {
        return new LedgerEntryResponse(
                entry.getSku(), entry.getChange(), entry.getReason(),
                entry.getReference(), entry.getOccurredAt());
    }
}
