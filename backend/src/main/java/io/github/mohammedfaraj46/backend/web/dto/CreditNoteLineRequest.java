package io.github.mohammedfaraj46.backend.web.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

/**
 * How much of one invoice line to credit.
 *
 * <p>Crediting happens on amounts, not on quantities: the user says "give back
 * 40 euro of this line", not "give back 2 of the 3 items". The VAT rate and type
 * are not here on purpose — they are inherited from the original line, so a
 * credit can never be issued at a different rate than was charged.
 *
 * @param invoiceLineId the line of the original invoice being credited
 * @param netAmount     the amount excluding VAT, positive; 0 leaves the line alone
 */
public record CreditNoteLineRequest(

        @NotNull(message = "invoiceLineId is required")
        Long invoiceLineId,

        @NotNull(message = "netAmount is required")
        @DecimalMin(value = "0.00", message = "netAmount cannot be negative")
        @Digits(integer = 10, fraction = 2, message = "netAmount may have at most 2 decimals")
        BigDecimal netAmount
) {
}
