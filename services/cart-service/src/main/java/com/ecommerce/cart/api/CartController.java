package com.ecommerce.cart.api;

import com.ecommerce.cart.api.dto.AddItemRequest;
import com.ecommerce.cart.api.dto.CartResponse;
import com.ecommerce.cart.api.dto.CartValidationResponse;
import com.ecommerce.cart.api.dto.UpdateQuantityRequest;
import com.ecommerce.cart.repository.CartOwner;
import com.ecommerce.cart.service.CartService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The caller's cart.
 *
 * <p>There is no {@code /api/cart/{id}} anywhere here, deliberately. A cart is always
 * derived from who is asking - a bearer token's {@code sub}, or the anonymous
 * {@code X-Cart-Token} header. An endpoint that took a cart id and trusted it would
 * let anyone read anyone's basket.
 *
 * <p>Singular {@code /api/cart}, not plural: there is exactly one active cart per
 * caller, so it is a singleton resource rather than a collection.
 */
@RestController
@RequestMapping("/api/cart")
@Tag(name = "Cart", description = "The current shopper's cart")
public class CartController {

    /**
     * Identifies an anonymous shopper's cart.
     *
     * <p>A header rather than a cookie, because the SPA already sends a bearer token
     * the same way and cookies would drag CSRF concerns into a stateless API.
     */
    public static final String CART_TOKEN_HEADER = "X-Cart-Token";

    private final CartService carts;

    public CartController(CartService carts) {
        this.carts = carts;
    }

    @GetMapping
    @Operation(summary = "Current cart, created empty if the caller has none")
    public CartResponse current(@AuthenticationPrincipal Jwt jwt,
                                @RequestHeader(value = CART_TOKEN_HEADER, required = false) String cartToken) {
        return carts.currentCart(owner(jwt, cartToken));
    }

    @PostMapping("/items")
    @Operation(summary = "Add a product, or increase its quantity if already present")
    public CartResponse addItem(@AuthenticationPrincipal Jwt jwt,
                                @RequestHeader(value = CART_TOKEN_HEADER, required = false) String cartToken,
                                @Valid @RequestBody AddItemRequest request) {
        return carts.addItem(owner(jwt, cartToken), request);
    }

    @PatchMapping("/items/{productId}")
    @Operation(summary = "Set an absolute quantity. Zero removes the line")
    public CartResponse setQuantity(@AuthenticationPrincipal Jwt jwt,
                                    @RequestHeader(value = CART_TOKEN_HEADER, required = false) String cartToken,
                                    @PathVariable String productId,
                                    @Valid @RequestBody UpdateQuantityRequest request) {
        return carts.setQuantity(owner(jwt, cartToken), productId, request.quantity());
    }

    @DeleteMapping("/items/{productId}")
    @Operation(summary = "Remove a line")
    public CartResponse removeItem(@AuthenticationPrincipal Jwt jwt,
                                   @RequestHeader(value = CART_TOKEN_HEADER, required = false) String cartToken,
                                   @PathVariable String productId) {
        return carts.removeItem(owner(jwt, cartToken), productId);
    }

    @DeleteMapping
    @Operation(summary = "Empty the cart")
    public ResponseEntity<CartResponse> clear(
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader(value = CART_TOKEN_HEADER, required = false) String cartToken) {
        // 200 with the now-empty cart rather than 204: the client needs the updated
        // totals to re-render, and a second GET to fetch them would be wasteful.
        return ResponseEntity.ok(carts.clear(owner(jwt, cartToken)));
    }

    @PostMapping("/merge")
    @Operation(summary = "Adopt an anonymous cart after signing in. Requires a token")
    public CartResponse merge(@AuthenticationPrincipal Jwt jwt,
                              @RequestHeader(CART_TOKEN_HEADER) String cartToken) {
        // Authentication is enforced by SecurityConfig, and the header is mandatory
        // here - merging without knowing which anonymous cart to adopt is meaningless.
        return carts.merge(jwt.getSubject(), cartToken);
    }

    @PostMapping("/validate")
    @Operation(summary = "Re-check prices and availability against the live catalog")
    public CartValidationResponse validate(
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader(value = CART_TOKEN_HEADER, required = false) String cartToken) {
        return carts.validate(owner(jwt, cartToken));
    }

    /**
     * Resolves who the cart belongs to.
     *
     * <p>A token wins over the header: once signed in, the customer's cart is the one
     * that matters, and a stale {@code X-Cart-Token} left in the SPA must not
     * redirect writes into an anonymous basket.
     */
    private static CartOwner owner(Jwt jwt, String cartToken) {
        return CartOwner.of(jwt == null ? null : jwt.getSubject(), cartToken);
    }
}
