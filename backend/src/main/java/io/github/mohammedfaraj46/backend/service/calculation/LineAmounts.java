package io.github.mohammedfaraj46.backend.service.calculation;

import java.math.BigDecimal;

/**
 * What one line comes to. All three are already rounded to two decimals — these
 * are the figures that appear on the invoice.
 *
 * @param netAmount quantity x unitPrice
 * @param vatAmount netAmount x vatRate
 * @param total     netAmount + vatAmount
 */
public record LineAmounts(BigDecimal netAmount, BigDecimal vatAmount, BigDecimal total) {
}
