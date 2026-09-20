package com.ecommerce.customer.repository;

import com.ecommerce.customer.domain.Address;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Addresses, always scoped to their owner.
 *
 * <p>Every lookup here takes the caller's {@code keycloakId} alongside the address
 * identifier. That is deliberate: it makes the ownership check part of the query
 * rather than something the service layer has to remember. A plain
 * {@code findByPublicId} would compile, work, and quietly let any authenticated
 * customer read any other customer's address - so it is not offered.
 *
 * <p>The underscore in {@code findByCustomer_KeycloakId} is Spring Data's explicit
 * property-path separator: traverse {@code Address.customer}, then read
 * {@code Customer.keycloakId}. Plain camelCase resolves the same way here, but the
 * underscore states the intent and cannot be misread as a property named
 * {@code customerKeycloakId}.
 */
public interface AddressRepository extends JpaRepository<Address, Long> {

    /** A customer's addresses, oldest first. */
    List<Address> findByCustomer_KeycloakIdOrderByCreatedAtAsc(String keycloakId);

    /**
     * One address, but only if it belongs to this caller.
     *
     * <p>Returning empty for "exists but belongs to someone else" is intentional:
     * the service turns that into a 404, not a 403. A 403 would confirm the id is
     * real, which leaks information about other customers' data.
     */
    Optional<Address> findByPublicIdAndCustomer_KeycloakId(UUID publicId, String keycloakId);

    long countByCustomer_KeycloakId(String keycloakId);

    /**
     * Clears the default-shipping flag on a customer's addresses.
     *
     * <p>Call this before setting a new default, so "at most one default per
     * customer" holds. The invariant cannot be expressed as a database constraint
     * portably - no partial unique index - so it lives here plus the service layer.
     *
     * <p>Two caveats that come with any bulk update:
     *
     * <ul>
     *   <li>{@code flushAutomatically} pushes pending changes to the database first,
     *       so this statement does not overwrite work the current transaction has not
     *       yet written. {@code clearAutomatically} then empties the persistence
     *       context, because entities already loaded would still hold the old flag in
     *       memory. The practical consequence: clear defaults <em>first</em>, then
     *       load the address you are promoting - any entity you were holding is now
     *       detached.</li>
     *   <li>A bulk update bypasses Hibernate, so it does <em>not</em> bump the
     *       {@code @Version} column. Optimistic locking will not notice these
     *       changes. Acceptable here, because it only ever flips a boolean the
     *       customer is explicitly reassigning.</li>
     * </ul>
     *
     * <p>{@code @Transactional} is required, not decoration: {@code @Modifying}
     * query methods get no transaction of their own, and {@code flushAutomatically}
     * then fails with "No EntityManager with actual transaction available". With it,
     * the method is safe to call standalone and joins the service's transaction when
     * there is one.
     *
     * @return how many rows were cleared
     */
    @Transactional
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update Address a
               set a.defaultShipping = false
             where a.customer.keycloakId = :keycloakId
               and a.defaultShipping = true
            """)
    int clearDefaultShipping(@Param("keycloakId") String keycloakId);

    /** Billing counterpart of {@link #clearDefaultShipping(String)}. */
    @Transactional
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update Address a
               set a.defaultBilling = false
             where a.customer.keycloakId = :keycloakId
               and a.defaultBilling = true
            """)
    int clearDefaultBilling(@Param("keycloakId") String keycloakId);
}
