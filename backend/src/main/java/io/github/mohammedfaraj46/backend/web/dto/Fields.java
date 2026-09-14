package io.github.mohammedfaraj46.backend.web.dto;

import java.util.Locale;

/**
 * Normalisation shared by the request records. See the package documentation
 * for why it happens here rather than in the services.
 */
final class Fields {

    private static final String DEFAULT_COUNTRY = "NL";

    private Fields() {
    }

    /**
     * A required text field: surrounding whitespace removed, null left alone so
     * {@code @NotBlank} is the one that reports it missing.
     */
    static String text(String value) {
        return value == null ? null : value.strip();
    }

    /**
     * An optional text field: absent and "nothing but spaces" both become null,
     * so the column holds either a real value or nothing — never an empty
     * string that has to be checked for separately everywhere it is read.
     */
    static String optionalText(String value) {
        if (value == null) {
            return null;
        }
        String stripped = value.strip();
        return stripped.isEmpty() ? null : stripped;
    }

    /** ISO 3166-1 alpha-2, upper-cased; absent means the home country. */
    static String countryCode(String value) {
        return value == null ? DEFAULT_COUNTRY : value.strip().toUpperCase(Locale.ROOT);
    }

    /** Lower-cased as well, so Foo@Example.nl and foo@example.nl are one address. */
    static String email(String value) {
        return value == null ? null : value.strip().toLowerCase(Locale.ROOT);
    }
}
