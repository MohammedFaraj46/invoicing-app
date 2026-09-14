package io.github.mohammedfaraj46.backend.web.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * One customer's invoices, as returned by GET /api/customers/{id}/invoices.
 *
 * <p>The list is wrapped rather than returned bare because the two figures are
 * about exactly this collection: computing them in the browser would mean
 * summing a list that is already summed in the database, and would go wrong the
 * moment the list is ever paged.
 *
 * @param totalInvoiced     everything that carries an invoice number, so drafts
 *                          are left out: they have not been invoiced yet
 * @param outstandingAmount sent and not yet paid
 * @param invoices          newest first; empty for a customer without invoices
 */
public record CustomerInvoicesResponse(
        BigDecimal totalInvoiced,
        BigDecimal outstandingAmount,
        List<InvoiceSummaryResponse> invoices
) {
}
