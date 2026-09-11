package io.github.mohammedfaraj46.backend.service.calculation;

import java.math.BigDecimal;

/**
 * One row of the VAT breakdown: everything on the invoice charged at the same
 * rate, added up. An invoice with 21% and 9% lines produces two of these.
 *
 * <p>Both figures are sums of already-rounded line amounts, so the breakdown
 * adds up exactly to the invoice totals.
 *
 * @param vatRate   the percentage, normalised to two decimals (21.00)
 * @param netAmount sum of the net amounts charged at this rate
 * @param vatAmount sum of the VAT amounts at this rate
 */
public record VatSubtotal(BigDecimal vatRate, BigDecimal netAmount, BigDecimal vatAmount) {
}
