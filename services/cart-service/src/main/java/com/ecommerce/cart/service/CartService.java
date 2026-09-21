package com.ecommerce.cart.service;

import com.ecommerce.cart.api.dto.AddItemRequest;
import com.ecommerce.cart.api.dto.CartResponse;
import com.ecommerce.cart.api.dto.CartValidationResponse;
import com.ecommerce.cart.config.CartProperties;
import com.ecommerce.cart.domain.Cart;
import com.ecommerce.cart.domain.CartItem;
import com.ecommerce.cart.domain.CartStatus;
import com.ecommerce.cart.repository.CartOwner;
import com.ecommerce.cart.repository.CartRepository;
import com.ecommerce.common.web.ConflictException;
import com.ecommerce.common.web.ResourceNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class CartService {

    private static final Logger log = LoggerFactory.getLogger(CartService.class);

    private final CartRepository carts;
    private final CatalogClient catalog;
    private final CartProperties properties;

    public CartService(CartRepository carts, CatalogClient catalog, CartProperties properties) {
        this.carts = carts;
        this.catalog = catalog;
        this.properties = properties;
    }

    // ------------------------------------------------------------------ reads

    /**
     * The caller's active cart, created empty if they have none.
     *
     * <p>Creating on read keeps the client simple - there is no "create cart" call to
     * make first, and no state where the SPA has to decide whether one exists.
     */
    @Transactional
    public CartResponse currentCart(CartOwner owner) {
        return CartResponse.from(findOrCreate(owner));
    }

    // ----------------------------------------------------------------- writes

    @Transactional
    public CartResponse addItem(CartOwner owner, AddItemRequest request) {
        Cart cart = findOrCreate(owner);

        // Name and price come from the catalog, never from the request. Trusting a
        // client-supplied price would let a caller set their own.
        CatalogClient.CatalogProduct product = catalog.findProduct(request.productId())
                .orElseThrow(() -> ResourceNotFoundException.of("Product", request.productId()));

        cart.addItem(product.id(), product.sku(), product.name(),
                product.price(), product.imageUrl(), request.quantityOrOne());
        cart.extendExpiry(ttlFor(owner));

        return CartResponse.from(carts.save(cart));
    }

    @Transactional
    public CartResponse setQuantity(CartOwner owner, String productId, int quantity) {
        Cart cart = requireActiveCart(owner);
        if (cart.findItem(productId).isEmpty()) {
            throw ResourceNotFoundException.of("Cart item", productId);
        }

        // Zero removes the line - see UpdateQuantityRequest, whose floor is 0 for
        // exactly this reason.
        cart.setItemQuantity(productId, quantity);
        cart.extendExpiry(ttlFor(owner));

        return CartResponse.from(carts.save(cart));
    }

    @Transactional
    public CartResponse removeItem(CartOwner owner, String productId) {
        Cart cart = requireActiveCart(owner);
        if (!cart.removeItem(productId)) {
            throw ResourceNotFoundException.of("Cart item", productId);
        }
        cart.extendExpiry(ttlFor(owner));
        return CartResponse.from(carts.save(cart));
    }

    @Transactional
    public CartResponse clear(CartOwner owner) {
        Cart cart = requireActiveCart(owner);
        cart.clear();
        return CartResponse.from(carts.save(cart));
    }

    /**
     * Moves an anonymous cart into the signed-in customer's cart.
     *
     * <p>Called by the SPA immediately after login. Without it, filling a basket and
     * then signing in to check out would silently discard the basket - which is the
     * single most annoying bug an e-commerce site can have.
     *
     * <p>Three cases, and the third is the one worth noticing:
     * <ol>
     *   <li>No anonymous cart: nothing to do.</li>
     *   <li>Customer has no cart yet: claim the anonymous one outright, no copying.</li>
     *   <li>Both exist: sum quantities per product, then empty the anonymous cart.</li>
     * </ol>
     */
    @Transactional
    public CartResponse merge(String customerId, String anonymousToken) {
        Optional<Cart> anonymous = carts.findByAnonymousTokenAndStatus(anonymousToken, CartStatus.ACTIVE);
        if (anonymous.isEmpty()) {
            return CartResponse.from(findOrCreate(new CartOwner.Customer(customerId)));
        }

        Optional<Cart> existing = carts.findByCustomerIdAndStatus(customerId, CartStatus.ACTIVE);
        if (existing.isEmpty()) {
            // Cheaper and lossless: hand the whole cart over rather than copying it
            // line by line into a new one.
            Cart claimed = anonymous.get();
            claimed.claimFor(customerId);
            claimed.extendExpiry(properties.ttlSignedIn());
            log.info("Claimed anonymous cart {} for customer", claimed.getPublicId());
            return CartResponse.from(carts.save(claimed));
        }

        Cart target = existing.get();
        Cart source = anonymous.get();
        target.mergeFrom(source);
        target.extendExpiry(properties.ttlSignedIn());

        carts.save(source);
        log.info("Merged anonymous cart {} into customer cart {}",
                source.getPublicId(), target.getPublicId());
        return CartResponse.from(carts.save(target));
    }

    /**
     * Marks the cart as checked out.
     *
     * <p>Called by order-service once an order has been created from it. Kept
     * separate from clearing so the cart's contents remain inspectable while the
     * order is still being processed.
     */
    @Transactional
    public void markCheckedOut(CartOwner owner) {
        Cart cart = requireActiveCart(owner);
        cart.markCheckedOut();
        carts.save(cart);
    }

    // ------------------------------------------------------------- validation

    /**
     * Re-checks every line against the live catalog.
     *
     * <p>This is what makes the price snapshots safe. The cart shows what the shopper
     * saw when they added each item; before checkout this compares that against the
     * catalog and reports the differences, so the UI can surface them rather than the
     * shopper discovering a new total on their statement.
     *
     * <p>Snapshots are refreshed to the current values as a side effect, so the cart
     * a shopper confirms is the cart they are charged for.
     */
    @Transactional
    public CartValidationResponse validate(CartOwner owner) {
        Cart cart = requireActiveCart(owner);
        List<CartValidationResponse.Issue> issues = new ArrayList<>();

        for (CartItem item : cart.getItems()) {
            Optional<CatalogClient.CatalogProduct> current = catalog.findProduct(item.getProductId());

            if (current.isEmpty()) {
                issues.add(CartValidationResponse.Issue.unavailable(
                        item.getProductId(), item.getProductName()));
                continue;
            }

            CatalogClient.CatalogProduct product = current.get();
            // compareTo, not equals: BigDecimal.equals is scale-sensitive, so
            // 179.00 and 179.0000 would compare unequal and report a phantom change.
            if (item.getUnitPrice().compareTo(product.price()) != 0) {
                issues.add(CartValidationResponse.Issue.priceChanged(
                        item.getProductId(), product.name(), item.getUnitPrice(), product.price()));
            }

            cart.addItem(product.id(), product.sku(), product.name(),
                    product.price(), product.imageUrl(), 0);
        }

        return CartValidationResponse.of(issues, CartResponse.from(carts.save(cart)));
    }

    // ----------------------------------------------------------------- shared

    private Cart findOrCreate(CartOwner owner) {
        return switch (owner) {
            case CartOwner.Customer customer -> carts
                    .findByCustomerIdAndStatus(customer.subject(), CartStatus.ACTIVE)
                    .orElseGet(() -> carts.save(
                            Cart.forCustomer(customer.subject(), properties.ttlSignedIn())));
            case CartOwner.Anonymous anonymous -> carts
                    .findByAnonymousTokenAndStatus(anonymous.token(), CartStatus.ACTIVE)
                    .orElseGet(() -> carts.save(
                            Cart.forAnonymous(anonymous.token(), properties.ttlAnonymous())));
        };
    }

    private Cart requireActiveCart(CartOwner owner) {
        Cart cart = findOrCreate(owner);
        if (!cart.isActive()) {
            throw new ConflictException("This cart has already been checked out");
        }
        return cart;
    }

    private Duration ttlFor(CartOwner owner) {
        return owner instanceof CartOwner.Customer
                ? properties.ttlSignedIn()
                : properties.ttlAnonymous();
    }
}
