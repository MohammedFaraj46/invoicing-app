package io.github.mohammedfaraj46.backend.service.calculation;

import java.math.BigDecimal;
import java.util.List;

/**
 * The complete result of a calculation: what each line comes to, the VAT split
 * per rate, and the three invoice totals.
 *
 * <p>These hold together exactly, with no rounding drift between them:
 * <ul>
 *   <li>{@code subtotalAmount} is the sum of the lines' net amounts</li>
 *   <li>{@code vatAmount} is the sum of the lines' VAT amounts</li>
 *   <li>{@code totalAmount} is the sum of the lines' totals, and also
 *       {@code subtotalAmount + vatAmount}</li>
 *   <li>the breakdown's net and VAT columns sum to {@code subtotalAmount} and
 *       {@code vatAmount}</li>
 * </ul>
 *
 * @param lines          per line, in the order they were given
 * @param vatBreakdown   one row per distinct rate, ascending by rate
 * @param subtotalAmount total excluding VAT
 * @param vatAmount      total VAT
 * @param totalAmount    total including VAT
 */
public record InvoiceAmounts(
        List<LineAmounts> lines,
        List<VatSubtotal> vatBreakdown,
        BigDecimal subtotalAmount,
        BigDecimal vatAmount,
        BigDecimal totalAmount
) {

    public InvoiceAmounts {
        lines = List.copyOf(lines);
        vatBreakdown = List.copyOf(vatBreakdown);
    }
}
