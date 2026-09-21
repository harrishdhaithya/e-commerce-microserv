package com.ecommerce.cart.service;

import com.ecommerce.cart.domain.Cart;
import com.ecommerce.cart.repository.CartRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Retires carts nobody has touched since they expired.
 *
 * <p>This job is what Redis TTL would have done for free. Dropping Redis (PLAN.md
 * section 2) traded a container for this class - a reasonable swap, but the cost is
 * real and worth naming: expiry is now approximate, bounded by the sweep interval,
 * rather than exact.
 *
 * <p>Batched rather than "find everything expired": a sweep that loads every stale
 * cart in one query is one that eventually runs out of memory instead of doing its
 * job. Whatever it does not finish, the next run picks up.
 */
@Component
public class CartExpirySweeper {

    private static final Logger log = LoggerFactory.getLogger(CartExpirySweeper.class);
    private static final int BATCH_SIZE = 200;

    private final CartRepository carts;

    public CartExpirySweeper(CartRepository carts) {
        this.carts = carts;
    }

    /**
     * Hourly, offset from startup rather than on the hour.
     *
     * <p>In Phase 3 this becomes one more thing to make idempotent: with several
     * replicas, every instance runs its own scheduler and they would sweep the same
     * carts concurrently. Harmless here - marking an expired cart expired twice
     * changes nothing - but the same pattern applied to the saga timeout would not be.
     */
    @Scheduled(initialDelay = 60_000, fixedDelay = 3_600_000)
    @Transactional
    public void sweep() {
        List<Cart> expired = carts.findExpired(Instant.now(), PageRequest.of(0, BATCH_SIZE));
        if (expired.isEmpty()) {
            return;
        }

        for (Cart cart : expired) {
            // Abandoned if it held items, merely expired if empty. The distinction is
            // what gives a Phase 3 abandoned-cart email something worth targeting.
            cart.markExpired();
        }
        carts.saveAll(expired);

        log.info("Expiry sweep retired {} cart(s){}", expired.size(),
                expired.size() == BATCH_SIZE ? " (batch full, more remain)" : "");
    }
}
