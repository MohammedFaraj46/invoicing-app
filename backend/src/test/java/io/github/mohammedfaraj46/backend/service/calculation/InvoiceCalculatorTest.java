package io.github.mohammedfaraj46.backend.service.calculation;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Pure unit tests: no Spring, no database.
 *
 * <p>Amounts are compared with {@code isEqualTo}, not
 * {@code isEqualByComparingTo}, so the scale is checked too — 121.0 is not an
 * acceptable answer where 121.00 is expected.
 */
@DisplayName("InvoiceCalculator")
class InvoiceCalculatorTest {

    private final InvoiceCalculator calculator = new InvoiceCalculator();

    private static BigDecimal decimal(String value) {
        return new BigDecimal(value);
    }

    @Nested
    @DisplayName("a single line")
    class SingleLine {

        @Test
        @DisplayName("100.00 at 21% is 21.00 VAT and 121.00 in total")
        void simpleLine() {
            InvoiceAmounts amounts = calculator.calculate(List.of(
                    CalculationLine.of("1", "100.00", "21")));

            LineAmounts line = amounts.lines().getFirst();
            assertThat(line.netAmount()).isEqualTo(decimal("100.00"));
            assertThat(line.vatAmount()).isEqualTo(decimal("21.00"));
            assertThat(line.total()).isEqualTo(decimal("121.00"));

            assertThat(amounts.subtotalAmount()).isEqualTo(decimal("100.00"));
            assertThat(amounts.vatAmount()).isEqualTo(decimal("21.00"));
            assertThat(amounts.totalAmount()).isEqualTo(decimal("121.00"));
        }

        @Test
        @DisplayName("3 x 19.99 rounds 12.5937 VAT up to 12.59")
        void threeTimesNineteenNinetyNine() {
            InvoiceAmounts amounts = calculator.calculate(List.of(
                    CalculationLine.of("3", "19.99", "21")));

            LineAmounts line = amounts.lines().getFirst();
            assertThat(line.netAmount()).isEqualTo(decimal("59.97"));
            // 59.97 x 21% = 12.5937, HALF_UP to two decimals
            assertThat(line.vatAmount()).isEqualTo(decimal("12.59"));
            assertThat(line.total()).isEqualTo(decimal("72.56"));
        }

        @Test
        @DisplayName("half an hour at 90.00 is 45.00 net")
        void fractionalQuantity() {
            InvoiceAmounts amounts = calculator.calculate(List.of(
                    CalculationLine.of("0.5", "90.00", "21")));

            LineAmounts line = amounts.lines().getFirst();
            assertThat(line.netAmount()).isEqualTo(decimal("45.00"));
            assertThat(line.vatAmount()).isEqualTo(decimal("9.45"));
            assertThat(line.total()).isEqualTo(decimal("54.45"));
        }

        @Test
        @DisplayName("a net amount ending in half a cent rounds up")
        void halfACentRoundsUp() {
            // 1.5 x 33.33 = 49.995
            InvoiceAmounts amounts = calculator.calculate(List.of(
                    CalculationLine.of("1.5", "33.33", "21")));

            LineAmounts line = amounts.lines().getFirst();
            assertThat(line.netAmount()).isEqualTo(decimal("50.00"));
            assertThat(line.vatAmount()).isEqualTo(decimal("10.50"));
            assertThat(line.total()).isEqualTo(decimal("60.50"));
        }

        @Test
        @DisplayName("a 0% line has no VAT and a total equal to its net amount")
        void zeroRate() {
            InvoiceAmounts amounts = calculator.calculate(List.of(
                    CalculationLine.of("2", "75.00", "0")));

            LineAmounts line = amounts.lines().getFirst();
            assertThat(line.netAmount()).isEqualTo(decimal("150.00"));
            assertThat(line.vatAmount()).isEqualTo(decimal("0.00"));
            assertThat(line.total()).isEqualTo(decimal("150.00"));

            assertThat(amounts.vatBreakdown()).singleElement()
                    .satisfies(row -> {
                        assertThat(row.vatRate()).isEqualTo(decimal("0.00"));
                        assertThat(row.vatAmount()).isEqualTo(decimal("0.00"));
                    });
        }
    }

    @Nested
    @DisplayName("rounding per line")
    class PerLineRounding {

        @Test
        @DisplayName("each line is rounded before the lines are added up")
        void roundsPerLineNotOnTheTotal() {
            // three lines of 0.5 x 0.25 = 0.125 each, at 0% to isolate the net rounding
            InvoiceAmounts amounts = calculator.calculate(List.of(
                    CalculationLine.of("0.5", "0.25", "0"),
                    CalculationLine.of("0.5", "0.25", "0"),
                    CalculationLine.of("0.5", "0.25", "0")));

            assertThat(amounts.lines()).allSatisfy(line ->
                    assertThat(line.netAmount()).isEqualTo(decimal("0.13")));

            // 0.13 x 3; adding at full precision first would have given 0.375 -> 0.38
            assertThat(amounts.subtotalAmount()).isEqualTo(decimal("0.39"));
            assertThat(amounts.subtotalAmount()).isNotEqualByComparingTo(decimal("0.38"));
        }

        @Test
        @DisplayName("the invoice total is exactly the sum of the rounded line totals")
        void totalIsSumOfRoundedLineTotals() {
            List<CalculationLine> lines = List.of(
                    CalculationLine.of("3", "19.99", "21"),
                    CalculationLine.of("0.5", "0.25", "9"),
                    CalculationLine.of("1.5", "33.33", "21"),
                    CalculationLine.of("7", "1.11", "9"),
                    CalculationLine.of("2", "75.00", "0"));

            InvoiceAmounts amounts = calculator.calculate(lines);

            BigDecimal summedNet = amounts.lines().stream()
                    .map(LineAmounts::netAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal summedVat = amounts.lines().stream()
                    .map(LineAmounts::vatAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal summedTotal = amounts.lines().stream()
                    .map(LineAmounts::total)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            assertThat(amounts.subtotalAmount()).isEqualByComparingTo(summedNet);
            assertThat(amounts.vatAmount()).isEqualByComparingTo(summedVat);
            assertThat(amounts.totalAmount()).isEqualByComparingTo(summedTotal);

            // and the three totals are consistent with each other
            assertThat(amounts.totalAmount())
                    .isEqualByComparingTo(amounts.subtotalAmount().add(amounts.vatAmount()));
        }
    }

    @Nested
    @DisplayName("VAT breakdown")
    class Breakdown {

        @Test
        @DisplayName("lines are grouped per rate, ascending")
        void groupsPerRate() {
            InvoiceAmounts amounts = calculator.calculate(List.of(
                    CalculationLine.of("2", "100.00", "21"),
                    CalculationLine.of("1", "50.00", "9"),
                    CalculationLine.of("3", "10.00", "21")));

            assertThat(amounts.vatBreakdown()).hasSize(2);

            VatSubtotal reduced = amounts.vatBreakdown().get(0);
            assertThat(reduced.vatRate()).isEqualTo(decimal("9.00"));
            assertThat(reduced.netAmount()).isEqualTo(decimal("50.00"));
            assertThat(reduced.vatAmount()).isEqualTo(decimal("4.50"));

            VatSubtotal standard = amounts.vatBreakdown().get(1);
            assertThat(standard.vatRate()).isEqualTo(decimal("21.00"));
            assertThat(standard.netAmount()).isEqualTo(decimal("230.00"));
            assertThat(standard.vatAmount()).isEqualTo(decimal("48.30"));

            assertThat(amounts.subtotalAmount()).isEqualTo(decimal("280.00"));
            assertThat(amounts.vatAmount()).isEqualTo(decimal("52.80"));
            assertThat(amounts.totalAmount()).isEqualTo(decimal("332.80"));
        }

        @Test
        @DisplayName("the breakdown adds up to the invoice totals")
        void breakdownMatchesTotals() {
            InvoiceAmounts amounts = calculator.calculate(List.of(
                    CalculationLine.of("3", "19.99", "21"),
                    CalculationLine.of("7", "1.11", "9"),
                    CalculationLine.of("2", "75.00", "0"),
                    CalculationLine.of("1", "12.34", "21")));

            BigDecimal breakdownNet = amounts.vatBreakdown().stream()
                    .map(VatSubtotal::netAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal breakdownVat = amounts.vatBreakdown().stream()
                    .map(VatSubtotal::vatAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            assertThat(breakdownNet).isEqualByComparingTo(amounts.subtotalAmount());
            assertThat(breakdownVat).isEqualByComparingTo(amounts.vatAmount());
        }

        @Test
        @DisplayName("the same rate written differently is still one group")
        void sameRateDifferentScaleIsOneGroup() {
            // 21, 21.0 and 21.00 are the same percentage; BigDecimal.equals() disagrees
            InvoiceAmounts amounts = calculator.calculate(List.of(
                    CalculationLine.of("1", "100.00", "21"),
                    CalculationLine.of("1", "100.00", "21.0"),
                    CalculationLine.of("1", "100.00", "21.00")));

            assertThat(amounts.vatBreakdown()).singleElement()
                    .satisfies(row -> {
                        assertThat(row.vatRate()).isEqualTo(decimal("21.00"));
                        assertThat(row.netAmount()).isEqualTo(decimal("300.00"));
                        assertThat(row.vatAmount()).isEqualTo(decimal("63.00"));
                    });
        }
    }

    @Nested
    @DisplayName("edge cases")
    class EdgeCases {

        @Test
        @DisplayName("no lines gives zeros at two decimals and an empty breakdown")
        void noLines() {
            InvoiceAmounts amounts = calculator.calculate(List.of());

            assertThat(amounts.lines()).isEmpty();
            assertThat(amounts.vatBreakdown()).isEmpty();
            assertThat(amounts.subtotalAmount()).isEqualTo(decimal("0.00"));
            assertThat(amounts.vatAmount()).isEqualTo(decimal("0.00"));
            assertThat(amounts.totalAmount()).isEqualTo(decimal("0.00"));
        }

        @Test
        @DisplayName("a quantity of zero is a free line, not an error")
        void zeroQuantity() {
            InvoiceAmounts amounts = calculator.calculate(List.of(
                    CalculationLine.of("0", "100.00", "21")));

            assertThat(amounts.totalAmount()).isEqualTo(decimal("0.00"));
        }

        @Test
        @DisplayName("results are immutable")
        void resultsAreImmutable() {
            InvoiceAmounts amounts = calculator.calculate(List.of(
                    CalculationLine.of("1", "100.00", "21")));

            assertThat(amounts.lines()).isUnmodifiable();
            assertThat(amounts.vatBreakdown()).isUnmodifiable();
        }
    }
}
