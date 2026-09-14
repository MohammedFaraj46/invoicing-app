package io.github.mohammedfaraj46.backend.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Payload for creating and updating a customer.
 *
 * <p>{@code country} is optional in the payload and defaults to "NL"; an
 * explicitly blank value is still rejected. Text is stripped before validation,
 * and the VAT number and postal code carry a length but no format — see the
 * package documentation.
 */
public record CustomerRequest(

        @NotBlank(message = "name is required")
        @Size(max = 255, message = "name must be at most 255 characters")
        String name,

        @NotBlank(message = "email is required")
        @Email(message = "email must be a valid email address")
        @Size(max = 255, message = "email must be at most 255 characters")
        String email,

        /**
         * Optional: needed for reverse-charge VAT and VIES lookups. An empty
         * field arrives as null, not as "". Length only, no pattern.
         */
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

    public CustomerRequest {
        name = Fields.text(name);
        email = Fields.email(email);
        vatNumber = Fields.optionalText(vatNumber);
        addressLine = Fields.text(addressLine);
        postalCode = Fields.text(postalCode);
        city = Fields.text(city);
        country = Fields.countryCode(country);
    }
}
