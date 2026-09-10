package io.github.mohammedfaraj46.backend.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Locale;

/**
 * Company details supplied when registering. Nested inside {@link RegisterRequest}.
 *
 * <p>{@code country} is optional in the payload and defaults to "NL"; an
 * explicitly blank value is still rejected.
 */
public record CompanyRegistration(

        @NotBlank(message = "name is required")
        @Size(max = 255, message = "name must be at most 255 characters")
        String name,

        @NotBlank(message = "kvkNumber is required")
        @Size(max = 20, message = "kvkNumber must be at most 20 characters")
        String kvkNumber,

        @NotBlank(message = "vatNumber is required")
        @Size(max = 20, message = "vatNumber must be at most 20 characters")
        String vatNumber,

        @NotBlank(message = "addressLine is required")
        @Size(max = 255, message = "addressLine must be at most 255 characters")
        String addressLine,

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

    private static final String DEFAULT_COUNTRY = "NL";

    public CompanyRegistration {
        if (country == null) {
            country = DEFAULT_COUNTRY;
        } else {
            country = country.trim().toUpperCase(Locale.ROOT);
        }
    }
}
