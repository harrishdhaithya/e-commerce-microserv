package com.ecommerce.customer.api;

import com.ecommerce.common.web.PagedResponse;
import com.ecommerce.customer.api.dto.CustomerResponse;
import com.ecommerce.customer.api.dto.UpdateCustomerRequest;
import com.ecommerce.customer.service.CustomerIdentity;
import com.ecommerce.customer.service.CustomerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/customers")
@Tag(name = "Customers", description = "Customer profiles")
public class CustomerController {

    private static final int MAX_PAGE_SIZE = 100;

    private final CustomerService customers;

    public CustomerController(CustomerService customers) {
        this.customers = customers;
    }

    /**
     * The calling customer, created from token claims on first request.
     *
     * <p>There is no {@code GET /api/customers/{id}} for self-service, and that is
     * the point: the caller is resolved from the validated token, never from a path
     * variable a client could change.
     */
    @GetMapping("/me")
    @Operation(summary = "Current customer, provisioned on first call")
    public CustomerResponse me(@AuthenticationPrincipal Jwt jwt) {
        return customers.resolveCurrent(CustomerIdentity.fromJwt(jwt));
    }

    @PatchMapping("/me")
    @Operation(summary = "Update own profile. Omitted fields are left unchanged")
    public CustomerResponse updateMe(@AuthenticationPrincipal Jwt jwt,
                                     @Valid @RequestBody UpdateCustomerRequest request) {
        return customers.updateCurrent(CustomerIdentity.fromJwt(jwt), request);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Fetch any customer by public id")
    public CustomerResponse getCustomer(@PathVariable UUID id) {
        return customers.getByPublicId(id);
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Search customers by partial email")
    public PagedResponse<CustomerResponse> search(
            @RequestParam(required = false) String email,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(MAX_PAGE_SIZE) int size) {

        return customers.search(email,
                PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "email")));
    }
}
