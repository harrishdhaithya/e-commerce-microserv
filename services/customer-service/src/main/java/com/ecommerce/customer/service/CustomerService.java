package com.ecommerce.customer.service;

import com.ecommerce.common.web.PagedResponse;
import com.ecommerce.common.web.ResourceNotFoundException;
import com.ecommerce.customer.api.dto.CustomerResponse;
import com.ecommerce.customer.api.dto.UpdateCustomerRequest;
import com.ecommerce.customer.domain.Customer;
import com.ecommerce.customer.repository.CustomerRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class CustomerService {

    private static final Logger log = LoggerFactory.getLogger(CustomerService.class);

    private final CustomerRepository customers;

    public CustomerService(CustomerRepository customers) {
        this.customers = customers;
    }

    /**
     * Returns the calling customer, creating them on first sight.
     *
     * <p>Just-in-time provisioning: Keycloak is the system of record for who exists,
     * so there is no registration endpoint here. The first authenticated request from
     * an unknown {@code sub} creates the row from the token's claims. That is simpler
     * than Keycloak webhooks or an SPI event listener, and it self-heals - a customer
     * whose row is somehow missing gets one on their next request.
     *
     * <p><strong>Deliberately not {@code @Transactional}.</strong> Two concurrent
     * first requests can both find nothing and both insert; the unique constraint on
     * {@code keycloak_id} rejects one of them. Recovering means re-reading, and a
     * transaction that has just taken a constraint violation is marked rollback-only,
     * so any read inside it would fail too. Leaving this method non-transactional
     * means each repository call gets its own short transaction and the retry is
     * free to succeed.
     *
     * <p>(Splitting it into a {@code @Transactional} private method would not work
     * either: self-invocation bypasses the proxy, so the annotation would be ignored
     * entirely - a trap worth knowing about.)
     */
    public CustomerResponse resolveCurrent(CustomerIdentity identity) {
        return CustomerResponse.from(resolveOrCreate(identity));
    }

    private Customer resolveOrCreate(CustomerIdentity identity) {
        var existing = customers.findByKeycloakId(identity.subject());
        if (existing.isPresent()) {
            Customer customer = existing.get();
            // Keycloak owns these; refresh so a name changed in the account console
            // does not leave this copy stale.
            customer.syncFromClaims(identity.email(), identity.firstName(), identity.lastName());
            return customers.save(customer);
        }

        try {
            Customer created = customers.save(Customer.fromClaims(
                    identity.subject(), identity.email(), identity.firstName(), identity.lastName()));
            log.info("Provisioned customer for subject {}", identity.subject());
            return created;
        } catch (DataIntegrityViolationException raced) {
            // Lost the race. The winner's row is committed, so a plain re-read wins.
            log.debug("Concurrent provisioning for subject {}, re-reading", identity.subject());
            return customers.findByKeycloakId(identity.subject())
                    .orElseThrow(() -> raced);
        }
    }

    /** The caller's own entity, for operations that need it. Provisions if absent. */
    Customer requireCurrent(CustomerIdentity identity) {
        return resolveOrCreate(identity);
    }

    @Transactional
    public CustomerResponse updateCurrent(CustomerIdentity identity, UpdateCustomerRequest request) {
        Customer customer = resolveOrCreate(identity);

        // null means "leave unchanged" - this backs a PATCH. Blanking a field is done
        // by sending an empty string, not by omitting it.
        if (request.firstName() != null) {
            customer.setFirstName(request.firstName());
        }
        if (request.lastName() != null) {
            customer.setLastName(request.lastName());
        }
        if (request.phone() != null) {
            customer.setPhone(request.phone());
        }
        if (request.marketingOptIn() != null) {
            customer.setMarketingOptIn(request.marketingOptIn());
        }

        return CustomerResponse.from(customers.save(customer));
    }

    @Transactional(readOnly = true)
    public CustomerResponse getByPublicId(UUID publicId) {
        return customers.findByPublicId(publicId)
                .map(CustomerResponse::from)
                .orElseThrow(() -> ResourceNotFoundException.of("Customer", publicId));
    }

    @Transactional(readOnly = true)
    public PagedResponse<CustomerResponse> search(String email, Pageable pageable) {
        String term = (email == null) ? "" : email.trim();
        return PagedResponse.from(
                customers.findByEmailContainingIgnoreCase(term, pageable), CustomerResponse::from);
    }
}
