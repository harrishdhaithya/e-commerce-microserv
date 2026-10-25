package com.ecommerce.inventory.api.dto;

import com.ecommerce.inventory.domain.LedgerReason;
import jakarta.validation.constraints.NotNull;

/**
 * A signed correction. Admin only.
 *
 * <p>The reason is required rather than defaulted: an adjustment without one is an
 * unexplained change in the audit trail, which defeats the point of having a ledger.
 */
public record AdjustmentRequest(int delta, @NotNull LedgerReason reason) {
}
