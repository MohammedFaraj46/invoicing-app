package io.github.mohammedfaraj46.backend.web.dto;

import io.github.mohammedfaraj46.backend.domain.Invoice;
import io.github.mohammedfaraj46.backend.domain.InvoiceStatus;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * An invoice as it appears in the list: enough for a table row, without the
 * lines.
 */
public record InvoiceSummaryResponse(
        Long id,
        String invoiceNumber,
        InvoiceStatus status,
        LocalDate invoiceDate,
        LocalDate dueDate,
        Long customerId,
        String customerName,
        BigDecimal totalAmount,
        String currency
) {

    public static InvoiceSummaryResponse from(Invoice invoice) {
        return new InvoiceSummaryResponse(
                invoice.getId(),
                invoice.getInvoiceNumber(),
                invoice.getStatus(),
                invoice.getInvoiceDate(),
                invoice.getDueDate(),
                invoice.getCustomer().getId(),
                invoice.getCustomer().getName(),
                invoice.getTotalAmount(),
                invoice.getCurrency());
    }
}
