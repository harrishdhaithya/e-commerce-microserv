package com.ecommerce.inventory.api;

import com.ecommerce.common.web.PagedResponse;
import com.ecommerce.inventory.api.dto.AdjustmentRequest;
import com.ecommerce.inventory.api.dto.AvailabilityRequest;
import com.ecommerce.inventory.api.dto.LedgerEntryResponse;
import com.ecommerce.inventory.api.dto.StockAdminResponse;
import com.ecommerce.inventory.api.dto.StockResponse;
import com.ecommerce.inventory.api.dto.StockUpsertRequest;
import com.ecommerce.inventory.repository.StockLedgerRepository;
import com.ecommerce.inventory.service.StockService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Stock levels.
 *
 * <p>Availability reads are public, because a storefront must be able to show "in
 * stock" to an anonymous visitor. Exact counts and every write require {@code ADMIN}.
 */
@RestController
@RequestMapping("/api/stock")
@Tag(name = "Stock", description = "Availability and stock administration")
public class StockController {

    private final StockService stock;
    private final StockLedgerRepository ledger;

    public StockController(StockService stock, StockLedgerRepository ledger) {
        this.stock = stock;
        this.ledger = ledger;
    }

    @GetMapping("/{sku}")
    @Operation(summary = "Availability for one SKU. Public, and without exact counts")
    public StockResponse availability(@PathVariable String sku) {
        return StockResponse.from(stock.requireItem(sku));
    }

    @PostMapping("/availability")
    @Operation(summary = "Batch availability, so a cart page is one call")
    public List<StockResponse> availability(@Valid @RequestBody AvailabilityRequest request) {
        // Unknown SKUs are simply absent from the response rather than a 404: a cart
        // containing one discontinued line should still render the rest.
        return stock.findAll(request.skus()).stream().map(StockResponse::from).toList();
    }

    // ------------------------------------------------------------------- admin

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Items at or below their reorder level")
    public List<StockAdminResponse> lowStock(
            @RequestParam(name = "belowReorderLevel", defaultValue = "true") boolean belowReorderLevel) {
        // A query parameter rather than a /low-stock path: "low stock" is a filter over
        // stock, not a separate resource. See docs/architecture.md.
        List<StockAdminResponse> all = stock.findBelowReorderLevel().stream()
                .map(StockAdminResponse::from).toList();
        return belowReorderLevel ? all : all;
    }

    @GetMapping("/{sku}/detail")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Full counters for one SKU")
    public StockAdminResponse detail(@PathVariable String sku) {
        return StockAdminResponse.from(stock.requireItem(sku));
    }

    @PutMapping("/{sku}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Set absolute stock. Creates the item if it does not exist")
    public StockAdminResponse upsert(@PathVariable String sku,
                                     @Valid @RequestBody StockUpsertRequest request) {
        return StockAdminResponse.from(
                stock.upsert(sku, request.quantityOnHand(), request.reorderLevel()));
    }

    @PostMapping("/{sku}/adjustments")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Signed correction with a reason, recorded in the ledger")
    public StockAdminResponse adjust(@PathVariable String sku,
                                     @Valid @RequestBody AdjustmentRequest request) {
        return StockAdminResponse.from(stock.adjust(sku, request.delta(), request.reason()));
    }

    @GetMapping("/{sku}/ledger")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Movement history, newest first")
    public PagedResponse<LedgerEntryResponse> ledger(
            @PathVariable String sku,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "50") @Min(1) @Max(200) int size) {

        return PagedResponse.from(
                ledger.findBySkuOrderByOccurredAtDesc(sku, PageRequest.of(page, size)),
                LedgerEntryResponse::from);
    }
}
