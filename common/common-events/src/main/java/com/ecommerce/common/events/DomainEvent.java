package com.ecommerce.common.events;

import java.time.Instant;

/**
 * Contract for everything published to Kafka.
 *
 * <p>Concrete events (OrderCreated, StockReserved, PaymentAuthorized, ...) arrive in
 * Phase 3 alongside the saga. The three fields below are what the infrastructure
 * needs regardless of payload:
 *
 * <ul>
 *   <li>{@code eventId} - the idempotency key. Consumers record processed IDs and
 *       ignore repeats, because Kafka guarantees at-least-once delivery, not
 *       exactly-once. See PLAN.md section 3.</li>
 *   <li>{@code occurredAt} - when the business fact happened, not when it was
 *       published. These differ whenever the outbox poller lags.</li>
 *   <li>{@code eventType} - routing and deserialization discriminator.</li>
 * </ul>
 */
public interface DomainEvent {

    String eventId();

    Instant occurredAt();

    String eventType();
}
