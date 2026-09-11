package io.github.mohammedfaraj46.backend.web.dto;

import io.github.mohammedfaraj46.backend.domain.Invoice;
import io.github.mohammedfaraj46.backend.domain.InvoiceStatus;
import io.github.mohammedfaraj46.backend.service.calculation.VatSubtotal;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * An invoice with its lines and VAT breakdown.
 *
 * <p>{@code invoiceNumber} and {@code invoiceDate} are null while the invoice is
 * a DRAFT; both are assigned when it is sent.
 */
public record InvoiceResponse(
        Long id,
        String invoiceNumber,
        InvoiceStatus status,
        LocalDate invoiceDate,
        LocalDate dueDate,
        int paymentTermDays,
        Long customerId,
        String customerName,
        BigDecimal subtotalAmount,
        BigDecimal vatAmount,
        BigDecimal totalAmount,
        String currency,
        String notes,
        List<InvoiceLineResponse> lines,
        List<VatSubtotalResponse> vatBreakdown
) {

    public static InvoiceResponse from(Invoice invoice, List<VatSubtotal> vatBreakdown) {
        return new InvoiceResponse(
                invoice.getId(),
                invoice.getInvoiceNumber(),
                invoice.getStatus(),
                invoice.getInvoiceDate(),
                invoice.getDueDate(),
                invoice.getPaymentTermDays(),
                invoice.getCustomer().getId(),
                invoice.getCustomer().getName(),
                invoice.getSubtotalAmount(),
                invoice.getVatAmount(),
                invoice.getTotalAmount(),
                invoice.getCurrency(),
                invoice.getNotes(),
                invoice.getLines().stream().map(InvoiceLineResponse::from).toList(),
                vatBreakdown.stream().map(VatSubtotalResponse::from).toList());
    }
}
