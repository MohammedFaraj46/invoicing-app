package io.github.mohammedfaraj46.backend.web.dto;

import io.github.mohammedfaraj46.backend.domain.CreditNoteLine;
import io.github.mohammedfaraj46.backend.domain.VatType;
import java.math.BigDecimal;

/**
 * One credit note line. The amounts are negative; quantity and unit price stay
 * positive so the line still reads as "1 x 40,00".
 */
public record CreditNoteLineResponse(
        Long id,
        int position,
        Long invoiceLineId,
        String description,
        BigDecimal quantity,
        BigDecimal unitPrice,
        BigDecimal vatRate,
        VatType vatType,
        BigDecimal lineNetAmount,
        BigDecimal lineVatAmount,
        BigDecimal lineTotal
) {

    public static CreditNoteLineResponse from(CreditNoteLine line) {
        return new CreditNoteLineResponse(
                line.getId(),
                line.getPosition(),
                line.getInvoiceLine().getId(),
                line.getDescription(),
                line.getQuantity(),
                line.getUnitPrice(),
                line.getVatRate(),
                line.getVatType(),
                line.getLineNetAmount(),
                line.getLineVatAmount(),
                line.getLineTotal());
    }
}
