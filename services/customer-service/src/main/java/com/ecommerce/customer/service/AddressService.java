package com.ecommerce.customer.service;

import com.ecommerce.common.web.ResourceNotFoundException;
import com.ecommerce.customer.api.dto.AddressRequest;
import com.ecommerce.customer.api.dto.AddressResponse;
import com.ecommerce.customer.domain.Address;
import com.ecommerce.customer.domain.Customer;
import com.ecommerce.customer.repository.AddressRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Addresses, always scoped to the caller.
 *
 * <p>Every method here takes a {@link CustomerIdentity} and every lookup passes its
 * subject to the repository. No method accepts a customer id from the request - that
 * is the difference between an API where ownership is enforced and one where it is
 * merely intended.
 */
@Service
public class AddressService {

    private final AddressRepository addresses;
    private final CustomerService customers;

    public AddressService(AddressRepository addresses, CustomerService customers) {
        this.addresses = addresses;
        this.customers = customers;
    }

    @Transactional(readOnly = true)
    public List<AddressResponse> list(CustomerIdentity identity) {
        return addresses.findByCustomer_KeycloakIdOrderByCreatedAtAsc(identity.subject())
                .stream()
                .map(AddressResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public AddressResponse get(CustomerIdentity identity, UUID publicId) {
        return AddressResponse.from(require(identity, publicId));
    }

    @Transactional
    public AddressResponse create(CustomerIdentity identity, AddressRequest request) {
        // Clear existing defaults BEFORE loading anything else. The bulk update runs
        // with clearAutomatically, which empties the persistence context - so any
        // entity loaded first would be detached and stale afterwards.
        clearDefaultsIfRequested(identity, request);

        Customer owner = customers.requireCurrent(identity);
        Address address = new Address(owner, request.recipientName(), request.line1(),
                request.city(), request.postalCode(), request.countryCode());
        apply(address, request);

        return AddressResponse.from(addresses.save(address));
    }

    @Transactional
    public AddressResponse replace(CustomerIdentity identity, UUID publicId, AddressRequest request) {
        // Ownership is checked first so a request for someone else's address fails
        // before it can clear this caller's defaults.
        require(identity, publicId);
        clearDefaultsIfRequested(identity, request);

        Address address = require(identity, publicId);
        address.setRecipientName(request.recipientName());
        address.setLine1(request.line1());
        address.setCity(request.city());
        address.setPostalCode(request.postalCode());
        address.setCountryCode(request.countryCode());
        apply(address, request);

        return AddressResponse.from(addresses.save(address));
    }

    @Transactional
    public void delete(CustomerIdentity identity, UUID publicId) {
        // Safe to hard-delete: order-service copies address values onto the order at
        // checkout rather than referencing this row, so no order depends on it.
        addresses.delete(require(identity, publicId));
    }

    @Transactional
    public AddressResponse setDefaultShipping(CustomerIdentity identity, UUID publicId) {
        addresses.clearDefaultShipping(identity.subject());

        // Loaded after clearing, so this instance is attached and current. If the id
        // is unknown the exception rolls the transaction back, undoing the clear -
        // which is why an invalid request cannot leave the customer with no default.
        Address address = require(identity, publicId);
        address.setDefaultShipping(true);
        return AddressResponse.from(addresses.save(address));
    }

    @Transactional
    public AddressResponse setDefaultBilling(CustomerIdentity identity, UUID publicId) {
        addresses.clearDefaultBilling(identity.subject());

        Address address = require(identity, publicId);
        address.setDefaultBilling(true);
        return AddressResponse.from(addresses.save(address));
    }

    /**
     * Loads an address that belongs to this caller, or throws.
     *
     * <p>Not found and belongs-to-someone-else are the same outcome on purpose: a 403
     * would confirm the id exists, which leaks information about other customers.
     */
    private Address require(CustomerIdentity identity, UUID publicId) {
        return addresses.findByPublicIdAndCustomer_KeycloakId(publicId, identity.subject())
                .orElseThrow(() -> ResourceNotFoundException.of("Address", publicId));
    }

    private void clearDefaultsIfRequested(CustomerIdentity identity, AddressRequest request) {
        if (request.defaultShipping()) {
            addresses.clearDefaultShipping(identity.subject());
        }
        if (request.defaultBilling()) {
            addresses.clearDefaultBilling(identity.subject());
        }
    }

    private static void apply(Address address, AddressRequest request) {
        address.setLabel(request.label());
        address.setLine2(request.line2());
        address.setRegion(request.region());
        address.setPhone(request.phone());
        address.setDefaultShipping(request.defaultShipping());
        address.setDefaultBilling(request.defaultBilling());
    }
}
