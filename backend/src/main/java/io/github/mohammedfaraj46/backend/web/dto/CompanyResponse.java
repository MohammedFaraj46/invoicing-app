package io.github.mohammedfaraj46.backend.web.dto;

import io.github.mohammedfaraj46.backend.domain.Company;

/**
 * The company profile of the signed-in user, as returned by
 * GET /api/companies/me.
 */
public record CompanyResponse(
        Long id,
        String name,
        String kvkNumber,
        String vatNumber,
        String addressLine,
        String postalCode,
        String city,
        String country,
        int defaultPaymentTermDays,
        String logoUrl
) {

    public static CompanyResponse from(Company company) {
        return new CompanyResponse(
                company.getId(),
                company.getName(),
                company.getKvkNumber(),
                company.getVatNumber(),
                company.getAddressLine(),
                company.getPostalCode(),
                company.getCity(),
                company.getCountry(),
                company.getDefaultPaymentTermDays(),
                company.getLogoUrl());
    }
}
