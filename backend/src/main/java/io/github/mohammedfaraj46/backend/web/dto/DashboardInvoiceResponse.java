package io.github.mohammedfaraj46.backend.web.dto;

import io.github.mohammedfaraj46.backend.domain.Invoice;
import io.github.mohammedfaraj46.backend.domain.InvoiceStatus;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One row in the dashboard's "most recent invoices" list.
 *
 * <p>Deliberately smaller than {@link InvoiceSummaryResponse}: the dashboard
 * shows a single date column, so a draft — which has no invoice date yet —
 * falls back to the day it was last touched. That keeps the list readable
 * without the caller having to decide which of several dates to render.
 *
 * <p>{@code invoiceNumber} is null while the invoice is a draft; the frontend
 * words that as "concept".
 */
public record DashboardInvoiceResponse(
        Long id,
        String invoiceNumber,
        InvoiceStatus status,
        String customerName,
        BigDecimal totalAmount,
        String currency,
        LocalDate date
) {

    public static DashboardInvoiceResponse from(Invoice invoice) {
        return new DashboardInvoiceResponse(
                invoice.getId(),
                invoice.getInvoiceNumber(),
                invoice.getStatus(),
                invoice.getCustomer().getName(),
                invoice.getTotalAmount(),
                invoice.getCurrency(),
                invoice.getInvoiceDate() != null
                        ? invoice.getInvoiceDate()
                        : invoice.getUpdatedAt().toLocalDate());
    }
}
