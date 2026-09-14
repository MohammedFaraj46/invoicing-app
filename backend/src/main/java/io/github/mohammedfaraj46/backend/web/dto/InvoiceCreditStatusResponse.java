package io.github.mohammedfaraj46.backend.web.dto;

import io.github.mohammedfaraj46.backend.domain.VatType;
import java.math.BigDecimal;
import java.util.List;

/**
 * How much of an invoice has been credited so far, per line.
 *
 * <p>Exists because the credit note form needs exactly this and nothing else can
 * supply it: the credit note list says which invoice each note belongs to, but
 * working out what is left per line from that would mean fetching every credit
 * note's detail one by one.
 *
 * @param lines       one entry per invoice line, with what is left to credit
 * @param creditNotes the credit notes already raised against this invoice
 */
public record InvoiceCreditStatusResponse(
        Long invoiceId,
        String invoiceNumber,
        List<Line> lines,
        List<CreditNoteSummaryResponse> creditNotes
) {

    /**
     * @param netAmount       what the line was invoiced for, excluding VAT
     * @param creditedAmount  already credited, as a positive amount
     * @param remainingAmount netAmount minus creditedAmount; the cap on a new credit
     */
    public record Line(
            Long invoiceLineId,
            int position,
            String description,
            BigDecimal vatRate,
            VatType vatType,
            BigDecimal netAmount,
            BigDecimal creditedAmount,
            BigDecimal remainingAmount
    ) {
    }
}
