package com.ecommerce.customer.api;

import com.ecommerce.customer.api.dto.AddressRequest;
import com.ecommerce.customer.api.dto.AddressResponse;
import com.ecommerce.customer.service.AddressService;
import com.ecommerce.customer.service.CustomerIdentity;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;
import java.util.UUID;

/**
 * A customer's own addresses.
 *
 * <p>Nested under {@code /me} rather than sitting at {@code /api/addresses}, because
 * an address genuinely is a sub-resource of one customer - and the path makes the
 * scoping impossible to misread.
 */
@RestController
@RequestMapping("/api/customers/me/addresses")
@Tag(name = "Addresses", description = "Shipping and billing addresses")
public class AddressController {

    private final AddressService addresses;

    public AddressController(AddressService addresses) {
        this.addresses = addresses;
    }

    @GetMapping
    @Operation(summary = "List own addresses, oldest first")
    public List<AddressResponse> list(@AuthenticationPrincipal Jwt jwt) {
        return addresses.list(CustomerIdentity.fromJwt(jwt));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Fetch one of your own addresses")
    public AddressResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return addresses.get(CustomerIdentity.fromJwt(jwt), id);
    }

    @PostMapping
    @Operation(summary = "Add an address")
    public ResponseEntity<AddressResponse> create(@AuthenticationPrincipal Jwt jwt,
                                                  @Valid @RequestBody AddressRequest request,
                                                  UriComponentsBuilder uriBuilder) {
        AddressResponse created = addresses.create(CustomerIdentity.fromJwt(jwt), request);

        // 201 with Location, so a client can follow the header rather than
        // reconstructing the URL itself.
        return ResponseEntity
                .created(uriBuilder.path("/api/customers/me/addresses/{id}")
                        .buildAndExpand(created.id())
                        .toUri())
                .body(created);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Replace an address")
    public AddressResponse replace(@AuthenticationPrincipal Jwt jwt,
                                   @PathVariable UUID id,
                                   @Valid @RequestBody AddressRequest request) {
        return addresses.replace(CustomerIdentity.fromJwt(jwt), id, request);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete an address")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        addresses.delete(CustomerIdentity.fromJwt(jwt), id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/default-shipping")
    @Operation(summary = "Make this the default shipping address, clearing any previous one")
    public AddressResponse setDefaultShipping(@AuthenticationPrincipal Jwt jwt,
                                              @PathVariable UUID id) {
        return addresses.setDefaultShipping(CustomerIdentity.fromJwt(jwt), id);
    }

    @PutMapping("/{id}/default-billing")
    @Operation(summary = "Make this the default billing address, clearing any previous one")
    public AddressResponse setDefaultBilling(@AuthenticationPrincipal Jwt jwt,
                                             @PathVariable UUID id) {
        return addresses.setDefaultBilling(CustomerIdentity.fromJwt(jwt), id);
    }
}
