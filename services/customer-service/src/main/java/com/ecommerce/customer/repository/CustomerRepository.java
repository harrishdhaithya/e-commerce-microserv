package com.ecommerce.customer.repository;

import com.ecommerce.customer.domain.Customer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CustomerRepository extends JpaRepository<Customer, Long> {

    /**
     * Looks a customer up by Keycloak's {@code sub} claim.
     *
     * <p>The entry point for just-in-time provisioning: on every authenticated
     * request the service resolves the caller through this method, and creates the
     * row if it returns empty.
     *
     * <p>Backed by the unique constraint {@code uq_customers_keycloak_id}, so this
     * can never match more than one row - which is why it returns Optional rather
     * than a List.
     */
    Optional<Customer> findByKeycloakId(String keycloakId);

    /**
     * Looks a customer up by their public id, for admin endpoints.
     *
     * <p>Never use this to serve {@code /me} - resolving the caller from a path
     * variable instead of from the token is how one customer ends up reading
     * another's data.
     */
    Optional<Customer> findByPublicId(UUID publicId);

    /**
     * Admin search by partial email, case-insensitive.
     *
     * <p>Paginated because the result set is unbounded: an admin searching for "a"
     * should not pull the whole table into memory.
     */
    Page<Customer> findByEmailContainingIgnoreCase(String email, Pageable pageable);
}
