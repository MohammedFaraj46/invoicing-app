package io.github.mohammedfaraj46.backend.web.dto;

import io.github.mohammedfaraj46.backend.domain.CreditNote;
import io.github.mohammedfaraj46.backend.domain.CreditNoteStatus;
import io.github.mohammedfaraj46.backend.service.calculation.VatSubtotal;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * A credit note with its lines and VAT breakdown.
 *
 * <p>{@code creditNoteNumber} and {@code creditDate} are null while it is a
 * DRAFT; both are assigned on send, from the credit note's own series.
 */
public record CreditNoteResponse(
        Long id,
        String creditNoteNumber,
        CreditNoteStatus status,
        LocalDate creditDate,
        Long originalInvoiceId,
        String originalInvoiceNumber,
        Long customerId,
        String customerName,
        BigDecimal subtotalAmount,
        BigDecimal vatAmount,
        BigDecimal totalAmount,
        String reason,
        List<CreditNoteLineResponse> lines,
        List<VatSubtotalResponse> vatBreakdown
) {

    public static CreditNoteResponse from(CreditNote creditNote, List<VatSubtotal> vatBreakdown) {
        return new CreditNoteResponse(
                creditNote.getId(),
                creditNote.getCreditNoteNumber(),
                creditNote.getStatus(),
                creditNote.getCreditDate(),
                creditNote.getOriginalInvoice().getId(),
                creditNote.getOriginalInvoice().getInvoiceNumber(),
                creditNote.getCustomer().getId(),
                creditNote.getCustomer().getName(),
                creditNote.getSubtotalAmount(),
                creditNote.getVatAmount(),
                creditNote.getTotalAmount(),
                creditNote.getReason(),
                creditNote.getLines().stream().map(CreditNoteLineResponse::from).toList(),
                vatBreakdown.stream().map(VatSubtotalResponse::from).toList());
    }
}
