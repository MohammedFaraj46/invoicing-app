package io.github.mohammedfaraj46.backend.web.dto;

import io.github.mohammedfaraj46.backend.domain.Customer;

/**
 * A customer as returned by the API. Carries no company id: a caller only ever
 * sees their own customers, so it would say nothing.
 */
public record CustomerResponse(
        Long id,
        String name,
        String email,
        String vatNumber,
        String addressLine,
        String postalCode,
        String city,
        String country
) {

    public static CustomerResponse from(Customer customer) {
        return new CustomerResponse(
                customer.getId(),
                customer.getName(),
                customer.getEmail(),
                customer.getVatNumber(),
                customer.getAddressLine(),
                customer.getPostalCode(),
                customer.getCity(),
                customer.getCountry());
    }
}
