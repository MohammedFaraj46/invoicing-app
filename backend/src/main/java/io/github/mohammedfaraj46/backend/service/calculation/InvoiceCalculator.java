package io.github.mohammedfaraj46.backend.service.calculation;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import org.springframework.stereotype.Service;

/**
 * Works out what an invoice comes to. Pure arithmetic: no database, no
 * entities, no state — give it lines, get amounts back.
 *
 * <h2>Rounding: per line, not on the total</h2>
 *
 * Every line amount is rounded to two decimals <em>before</em> anything is added
 * up, and the invoice totals are sums of those rounded figures.
 *
 * <p>The alternative — adding up at full precision and rounding once at the end —
 * produces a total that can differ by a cent from the numbers printed on the
 * invoice. Three lines of 0.125 show it: rounded per line that is
 * 0.13 + 0.13 + 0.13 = 0.39, rounded at the end it is 0.375 → 0.38. A reader who
 * adds up the visible lines gets 0.39, so 0.38 would look like an error. The
 * lines are what the customer sees and checks, so the lines win.
 *
 * <p>{@link RoundingMode#HALF_UP} is the choice throughout: it is what people
 * expect from money (half a cent goes up) and it is symmetric around zero, which
 * matters once credit notes start producing negative amounts.
 */
@Service
public class InvoiceCalculator {

    /** Two decimals: the smallest unit of the currency. */
    public static final int MONEY_SCALE = 2;

    /** See the class comment for why HALF_UP. */
    public static final RoundingMode ROUNDING_MODE = RoundingMode.HALF_UP;

    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");
    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(MONEY_SCALE, ROUNDING_MODE);

    /** Scale for a VAT percentage, e.g. 21.00. */
    private static final int RATE_SCALE = 2;

    /**
     * An amount that came out of a database aggregate, brought to the app's
     * money scale. A sum over no rows is null in SQL rather than zero, and a
     * screen that shows money should never have to think about that.
     */
    public static BigDecimal toMoney(BigDecimal amount) {
        return amount == null ? ZERO : amount.setScale(MONEY_SCALE, ROUNDING_MODE);
    }

    public InvoiceAmounts calculate(List<CalculationLine> lines) {
        Objects.requireNonNull(lines, "lines is required");

        List<LineAmounts> lineAmounts = new ArrayList<>(lines.size());
        List<VatSubtotal> perLine = new ArrayList<>(lines.size());

        BigDecimal subtotal = ZERO;
        BigDecimal totalVat = ZERO;
        BigDecimal total = ZERO;

        for (CalculationLine line : lines) {
            LineAmounts amounts = calculateLine(line);
            lineAmounts.add(amounts);
            perLine.add(new VatSubtotal(line.vatRate(), amounts.netAmount(), amounts.vatAmount()));

            subtotal = subtotal.add(amounts.netAmount());
            totalVat = totalVat.add(amounts.vatAmount());
            total = total.add(amounts.total());
        }

        return new InvoiceAmounts(lineAmounts, groupByRate(perLine), subtotal, totalVat, total);
    }

    /**
     * Adds up per-line amounts into one row per VAT rate.
     *
     * <p>Separate from {@link #calculate} so a stored invoice can have its
     * breakdown rebuilt by grouping the amounts that were saved, rather than by
     * recalculating them — which is what keeps a sent invoice's snapshot intact.
     *
     * @param perLine one entry per line: its rate, net amount and VAT amount
     * @return one entry per distinct rate, ascending
     */
    public List<VatSubtotal> groupByRate(List<VatSubtotal> perLine) {
        Objects.requireNonNull(perLine, "perLine is required");

        // TreeMap and not HashMap on purpose: BigDecimal.equals() compares scale,
        // so 21 and 21.00 would be two different keys in a hash map. TreeMap uses
        // compareTo, which groups them — and it keeps the breakdown ordered by rate.
        Map<BigDecimal, VatAccumulator> byRate = new TreeMap<>();

        for (VatSubtotal line : perLine) {
            byRate.computeIfAbsent(normaliseRate(line.vatRate()), rate -> new VatAccumulator())
                    .add(line.netAmount(), line.vatAmount());
        }

        return byRate.entrySet().stream()
                .map(entry -> new VatSubtotal(entry.getKey(), entry.getValue().net, entry.getValue().vat))
                .toList();
    }

    /**
     * One line, rounded. The VAT is taken over the <em>rounded</em> net amount,
     * so the three figures on the line are consistent with each other:
     * total = net + vat, exactly.
     */
    public LineAmounts calculateLine(CalculationLine line) {
        Objects.requireNonNull(line, "line is required");

        BigDecimal net = line.quantity()
                .multiply(line.unitPrice())
                .setScale(MONEY_SCALE, ROUNDING_MODE);

        // multiply first, then divide by 100: dividing the rate by 100 up front
        // would need its own rounding and lose precision before it is used
        BigDecimal vat = net.multiply(line.vatRate())
                .divide(ONE_HUNDRED, MONEY_SCALE, ROUNDING_MODE);

        return new LineAmounts(net, vat, net.add(vat));
    }

    private static BigDecimal normaliseRate(BigDecimal vatRate) {
        return vatRate.setScale(RATE_SCALE, ROUNDING_MODE);
    }

    /** Running total for one VAT rate. */
    private static final class VatAccumulator {
        private BigDecimal net = ZERO;
        private BigDecimal vat = ZERO;

        void add(BigDecimal lineNet, BigDecimal lineVat) {
            net = net.add(lineNet);
            vat = vat.add(lineVat);
        }
    }
}
