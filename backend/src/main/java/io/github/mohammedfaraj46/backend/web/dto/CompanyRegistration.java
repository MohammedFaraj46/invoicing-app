package io.github.mohammedfaraj46.backend.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Company details supplied when registering. Nested inside {@link RegisterRequest}.
 *
 * <p>{@code country} is optional in the payload and defaults to "NL"; an
 * explicitly blank value is still rejected. The other fields are stripped before
 * validation — see the package documentation, which also covers why the KvK
 * number, VAT number and postal code carry a length but no format.
 */
public record CompanyRegistration(

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
        String country
) {

    public CompanyRegistration {
        name = Fields.text(name);
        kvkNumber = Fields.text(kvkNumber);
        vatNumber = Fields.text(vatNumber);
        addressLine = Fields.text(addressLine);
        postalCode = Fields.text(postalCode);
        city = Fields.text(city);
        country = Fields.countryCode(country);
    }
}
