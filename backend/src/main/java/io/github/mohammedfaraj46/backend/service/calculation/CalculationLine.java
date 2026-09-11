package io.github.mohammedfaraj46.backend.service.calculation;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * One line of input for {@link InvoiceCalculator}.
 *
 * <p>Deliberately not a JPA entity: the calculator is pure arithmetic and knows
 * nothing about invoices, credit notes or the database. Callers map their own
 * lines onto this.
 *
 * @param quantity  how many units; may have decimals (half an hour is 0.5)
 * @param unitPrice price of one unit
 * @param vatRate   the applied percentage, so 21 means 21% — not 0.21
 */
public record CalculationLine(BigDecimal quantity, BigDecimal unitPrice, BigDecimal vatRate) {

    public CalculationLine {
        Objects.requireNonNull(quantity, "quantity is required");
        Objects.requireNonNull(unitPrice, "unitPrice is required");
        Objects.requireNonNull(vatRate, "vatRate is required");
    }

    /** Convenience for callers and tests that have plain strings. */
    public static CalculationLine of(String quantity, String unitPrice, String vatRate) {
        return new CalculationLine(new BigDecimal(quantity), new BigDecimal(unitPrice), new BigDecimal(vatRate));
    }
}
