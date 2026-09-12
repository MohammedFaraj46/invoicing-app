package io.github.mohammedfaraj46.backend.service.pdf;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Turns amounts, rates and dates into the text that goes on the invoice.
 *
 * <p>Dutch conventions throughout: a comma for decimals and a dot for
 * thousands, dates as 11-09-2026. The invoice goes to a Dutch customer, so the
 * document is formatted for that reader regardless of the server's own locale —
 * which is why the locale is pinned here instead of left to the default.
 */
final class PdfFormatter {

    private static final Locale DUTCH = Locale.forLanguageTag("nl-NL");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd-MM-yyyy");

    private PdfFormatter() {
    }

    /** 1234.5 becomes "1.234,50" — without a currency symbol, the column header carries that. */
    static String amount(BigDecimal value) {
        NumberFormat format = NumberFormat.getNumberInstance(DUTCH);
        format.setMinimumFractionDigits(2);
        format.setMaximumFractionDigits(2);
        return format.format(value == null ? BigDecimal.ZERO : value);
    }

    /** Quantities keep their meaningful decimals: 3 stays "3", 0.5 becomes "0,5". */
    static String quantity(BigDecimal value) {
        if (value == null) {
            return "0";
        }
        NumberFormat format = NumberFormat.getNumberInstance(DUTCH);
        format.setMinimumFractionDigits(0);
        format.setMaximumFractionDigits(3);
        return format.format(value.stripTrailingZeros());
    }

    /** 21.00 becomes "21%", 9.50 becomes "9,5%". */
    static String rate(BigDecimal value) {
        if (value == null) {
            return "0%";
        }
        // min 0 / max 2 fraction digits already drops the trailing zeros
        NumberFormat format = NumberFormat.getNumberInstance(DUTCH);
        format.setMinimumFractionDigits(0);
        format.setMaximumFractionDigits(2);
        return format.format(value) + "%";
    }

    static String date(LocalDate value) {
        return value == null ? "" : value.format(DATE);
    }
}
