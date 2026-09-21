package com.ecommerce.cart.repository;

import com.ecommerce.cart.domain.Cart;
import com.ecommerce.cart.domain.CartStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Carts, always scoped to their owner.
 *
 * <p>Same principle as AddressRepository in customer-service: there is no
 * {@code findByPublicId}. A cart is reached through whoever owns it, so a lookup with
 * the wrong owner returns nothing rather than someone else's basket.
 *
 * <p>{@code @EntityGraph} on the lookups fetches items in the same query. Without it,
 * rendering a cart would fire one query for the cart and another for its lines - the
 * N+1 problem, in its smallest form.
 */
public interface CartRepository extends JpaRepository<Cart, Long> {

    @EntityGraph(attributePaths = "items")
    Optional<Cart> findByCustomerIdAndStatus(String customerId, CartStatus status);

    @EntityGraph(attributePaths = "items")
    Optional<Cart> findByAnonymousTokenAndStatus(String anonymousToken, CartStatus status);

    /**
     * Carts due for the expiry sweep.
     *
     * <p>Limited by the caller rather than fetching everything: a sweep that tries to
     * load every expired cart at once is one that eventually runs out of memory
     * instead of doing its job.
     */
    @Query("""
            select c from Cart c
             where c.status = com.ecommerce.cart.domain.CartStatus.ACTIVE
               and c.expiresAt < :now
             order by c.expiresAt asc
            """)
    List<Cart> findExpired(@Param("now") Instant now, org.springframework.data.domain.Pageable pageable);

    long countByStatus(CartStatus status);
}
