package io.github.mohammedfaraj46.backend.web.dto;

import io.github.mohammedfaraj46.backend.domain.InvoiceLine;
import io.github.mohammedfaraj46.backend.domain.VatType;
import java.math.BigDecimal;

/**
 * One invoice line, including the stored amount snapshot.
 */
public record InvoiceLineResponse(
        Long id,
        int position,
        String description,
        BigDecimal quantity,
        BigDecimal unitPrice,
        BigDecimal vatRate,
        VatType vatType,
        BigDecimal lineNetAmount,
        BigDecimal lineVatAmount,
        BigDecimal lineTotal
) {

    public static InvoiceLineResponse from(InvoiceLine line) {
        return new InvoiceLineResponse(
                line.getId(),
                line.getPosition(),
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
