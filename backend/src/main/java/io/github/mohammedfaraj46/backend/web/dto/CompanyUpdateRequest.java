package io.github.mohammedfaraj46.backend.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * The company profile as the owner may change it, for PUT /api/companies/me.
 *
 * <p>The same fields and the same rules as {@link CompanyRegistration}, plus the
 * default payment term. There is no id: which company is being changed follows
 * from the login cookie.
 *
 * <p>{@code logoUrl} is not here on purpose. Nothing in the app sets it yet, and
 * a field that the form cannot fill would be blanked on every save.
 *
 * <p>Text is stripped before validation, and the KvK number, VAT number and
 * postal code are checked for length but never for format — see the package
 * documentation.
 */
public record CompanyUpdateRequest(

        @NotBlank(message = "name is required")
        @Size(max = 255, message = "name must be at most 255 characters")
        String name,

        // length only, no pattern: not country-specific on purpose
        @NotBlank(message = "kvkNumber is required")
        @Size(max = 20, message = "kvkNumber must be at most 20 characters")
        String kvkNumber,

        @NotBlank(message = "vatNumber is required")
        @Size(max = 20, message = "vatNumber must be at most 20 characters")
        String vatNumber,

        @NotBlank(message = "addressLine is required")
        @Size(max = 255, message = "addressLine must be at most 255 characters")
        String addressLine,

        // length only, no pattern: not country-specific on purpose
        @NotBlank(message = "postalCode is required")
        @Size(max = 20, message = "postalCode must be at most 20 characters")
        String postalCode,

        @NotBlank(message = "city is required")
        @Size(max = 100, message = "city must be at most 100 characters")
        String city,

        @NotBlank(message = "country is required")
        @Size(min = 2, max = 2, message = "country must be a 2-letter ISO code")
        String country,

        @NotNull(message = "defaultPaymentTermDays is required")
        @Positive(message = "defaultPaymentTermDays must be greater than zero")
        Integer defaultPaymentTermDays
) {

    public CompanyUpdateRequest {
        name = Fields.text(name);
        kvkNumber = Fields.text(kvkNumber);
        vatNumber = Fields.text(vatNumber);
        addressLine = Fields.text(addressLine);
        postalCode = Fields.text(postalCode);
        city = Fields.text(city);
        country = Fields.countryCode(country);
    }
}
