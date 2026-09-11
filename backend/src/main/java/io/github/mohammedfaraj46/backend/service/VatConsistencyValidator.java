package io.github.mohammedfaraj46.backend.service;

import io.github.mohammedfaraj46.backend.domain.VatType;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Checks that a line's VAT type and rate agree with each other.
 *
 * <p>Nothing stops a caller from sending {@code REVERSE_CHARGE} with 21%, and the
 * calculator would dutifully charge 21% VAT on a line where none is due. The
 * rule below is what keeps that from happening — and it is also why the
 * calculator needs no special case for reverse charge: the rate is forced to 0,
 * so 0 VAT falls out of the ordinary arithmetic.
 *
 * <ul>
 *   <li>{@code ZERO} and {@code REVERSE_CHARGE}: the rate must be 0</li>
 *   <li>{@code STANDARD} and {@code REDUCED}: the rate must be above 0</li>
 * </ul>
 */
public final class VatConsistencyValidator {

    private VatConsistencyValidator() {
    }

    /** One line's VAT pair, in the order the lines appear on the invoice. */
    public record Line(VatType vatType, BigDecimal vatRate) {
    }

    /**
     * @throws InvoiceValidationException if any line's type and rate disagree,
     *         with one message per offending line keyed as {@code lines[n].vatRate}
     */
    public static void validate(List<Line> lines) {
        Map<String, String> errors = new LinkedHashMap<>();

        for (int index = 0; index < lines.size(); index++) {
            Line line = lines.get(index);
            if (line.vatType() == null || line.vatRate() == null) {
                // bean validation already rejects these; nothing to compare here
                continue;
            }

            boolean zeroRated = line.vatType() == VatType.ZERO
                    || line.vatType() == VatType.REVERSE_CHARGE;
            boolean rateIsZero = line.vatRate().compareTo(BigDecimal.ZERO) == 0;

            if (zeroRated && !rateIsZero) {
                errors.put("lines[" + index + "].vatRate",
                        "vatRate must be 0 for vatType " + line.vatType());
            } else if (!zeroRated && rateIsZero) {
                errors.put("lines[" + index + "].vatRate",
                        "vatRate must be greater than 0 for vatType " + line.vatType());
            }
        }

        if (!errors.isEmpty()) {
            throw new InvoiceValidationException(errors);
        }
    }
}
