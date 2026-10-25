package com.ecommerce.inventory.service;

import com.ecommerce.common.web.ConflictException;
import com.ecommerce.inventory.domain.StockReservation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Retries stock operations that lost an optimistic-lock race.
 *
 * <p>When two requests reserve the last item concurrently, both read
 * {@code version = 3}. The first commits and the row becomes {@code version = 4}. The
 * second's update carries {@code WHERE version = 3}, matches zero rows, and Hibernate
 * raises {@code OptimisticLockingFailureException}. Nothing was overwritten — which is
 * the point — but the second request must look again and decide afresh, because by
 * then there may be no stock left.
 *
 * <p><strong>Why this is a separate bean from {@link StockService}, and why
 * {@code @Retryable} does not change that:</strong>
 *
 * <ul>
 *   <li>A transaction that has taken that exception is marked rollback-only. Retrying
 *       <em>inside</em> it fails too, so each attempt must be a new transaction.</li>
 *   <li>{@code @Retryable} is proxy-based, exactly like {@code @Transactional}. Put
 *       both on the same method and you are relying on advisor ordering to decide
 *       whether the retry wraps the transaction or sits inside it — and inside is
 *       useless. A self-invocation bypasses both entirely.</li>
 * </ul>
 *
 * <p>So the arrangement is unchanged: retry here, transaction on another bean. Only
 * the loop has gone.
 */
@Component
public class StockReservationCoordinator {

    private static final Logger log = LoggerFactory.getLogger(StockReservationCoordinator.class);

    private final StockService stockService;

    public StockReservationCoordinator(StockService stockService) {
        this.stockService = stockService;
    }

    /**
     * Holds stock for an order, retrying if another writer got there first.
     *
     * <p>Each attempt is a fresh call through the proxy, so a fresh transaction and a
     * fresh read of the current version. Bounded by
     * {@code inventory.max-reserve-attempts}: a SKU under genuine contention should
     * fail loudly rather than spin, holding a connection from a pool of five.
     *
     * <p>The backoff is exponential with jitter, which matters more than it looks. A
     * fixed delay makes contending threads collide again in lockstep; randomising it
     * spreads them out.
     */
    @Retryable(
            retryFor = OptimisticLockingFailureException.class,
            maxAttemptsExpression = "${inventory.max-reserve-attempts}",
            backoff = @Backoff(
                    delayExpression = "${inventory.retry-backoff-ms}",
                    multiplier = 2,
                    random = true))
    public StockReservation reserve(String orderId, String sku, int quantity) {
        return stockService.reserve(orderId, sku, quantity);
    }

    @Retryable(
            retryFor = OptimisticLockingFailureException.class,
            maxAttemptsExpression = "${inventory.max-reserve-attempts}",
            backoff = @Backoff(
                    delayExpression = "${inventory.retry-backoff-ms}",
                    multiplier = 2,
                    random = true))
    public StockReservation commit(UUID reservationId) {
        return stockService.commit(reservationId);
    }

    @Retryable(
            retryFor = OptimisticLockingFailureException.class,
            maxAttemptsExpression = "${inventory.max-reserve-attempts}",
            backoff = @Backoff(
                    delayExpression = "${inventory.retry-backoff-ms}",
                    multiplier = 2,
                    random = true))
    public StockReservation release(UUID reservationId) {
        return stockService.release(reservationId);
    }

    // ------------------------------------------------------------- recovery
    //
    // Two things about @Recover that are easy to get wrong, and both cost real time:
    //
    // 1. It is matched on return type, the throwable, AND the intercepted method's
    //    remaining parameters. A single varargs recoverer matches nothing.
    //
    // 2. Once ANY @Recover exists on the class, Spring Retry routes EVERY terminal
    //    exception through recoverer lookup - not only the retryable ones. These
    //    methods therefore declare RuntimeException and rethrow anything that is not
    //    an optimistic-lock failure. Without that, a perfectly ordinary
    //    ConflictException ("this reservation is already COMMITTED") finds no matching
    //    recoverer and is replaced by ExhaustedRetryException, destroying the 409 the
    //    caller needed.

    @Recover
    StockReservation reserveExhausted(RuntimeException e,
                                      String orderId, String sku, int quantity) {
        rethrowIfNotContention(e);
        log.warn("Gave up reserving {} x {} for order {} after repeated conflicts",
                quantity, sku, orderId);
        throw contended();
    }

    @Recover
    StockReservation settleExhausted(RuntimeException e, UUID reservationId) {
        rethrowIfNotContention(e);
        log.warn("Gave up settling reservation {} after repeated conflicts", reservationId);
        throw contended();
    }

    /**
     * Lets business failures out untouched.
     *
     * <p>InsufficientStock and "already committed" are not contention; they are the
     * answer. Converting them here would turn a meaningful 409 into a misleading one.
     */
    private static void rethrowIfNotContention(RuntimeException e) {
        if (!(e instanceof OptimisticLockingFailureException)) {
            throw e;
        }
    }

    /**
     * Nothing is broken — the row is simply contended. A 409 says "try again"; letting
     * the raw {@code OptimisticLockingFailureException} escape would say "we are
     * broken", which is both untrue and unactionable.
     */
    private static ConflictException contended() {
        return new ConflictException(
                "Stock for this item is being updated by another request. Please retry.");
    }
}
