package io.github.mohammedfaraj46.backend.web.dto;

import io.github.mohammedfaraj46.backend.domain.CreditNote;
import io.github.mohammedfaraj46.backend.domain.CreditNoteStatus;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * A credit note as it appears in a list: enough for a table row, without the
 * lines.
 */
public record CreditNoteSummaryResponse(
        Long id,
        String creditNoteNumber,
        CreditNoteStatus status,
        LocalDate creditDate,
        Long originalInvoiceId,
        String originalInvoiceNumber,
        Long customerId,
        String customerName,
        BigDecimal totalAmount
) {

    public static CreditNoteSummaryResponse from(CreditNote creditNote) {
        return new CreditNoteSummaryResponse(
                creditNote.getId(),
                creditNote.getCreditNoteNumber(),
                creditNote.getStatus(),
                creditNote.getCreditDate(),
                creditNote.getOriginalInvoice().getId(),
                creditNote.getOriginalInvoice().getInvoiceNumber(),
                creditNote.getCustomer().getId(),
                creditNote.getCustomer().getName(),
                creditNote.getTotalAmount());
    }
}
