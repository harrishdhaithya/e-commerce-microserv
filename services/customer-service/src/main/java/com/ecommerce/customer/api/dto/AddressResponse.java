package com.ecommerce.customer.api.dto;

import com.ecommerce.customer.domain.Address;

import java.util.UUID;

public record AddressResponse(
        UUID id,
        String label,
        String recipientName,
        String line1,
        String line2,
        String city,
        String region,
        String postalCode,
        String countryCode,
        String phone,
        boolean defaultShipping,
        boolean defaultBilling) {

    public static AddressResponse from(Address address) {
        return new AddressResponse(
                address.getPublicId(),
                address.getLabel(),
                address.getRecipientName(),
                address.getLine1(),
                address.getLine2(),
                address.getCity(),
                address.getRegion(),
                address.getPostalCode(),
                address.getCountryCode(),
                address.getPhone(),
                address.isDefaultShipping(),
                address.isDefaultBilling());
    }
}
