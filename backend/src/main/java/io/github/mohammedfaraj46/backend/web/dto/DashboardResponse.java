package io.github.mohammedfaraj46.backend.web.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * The figures behind the dashboard, in one response.
 *
 * <p>One call rather than four: the numbers belong together on screen, and
 * asking for them separately would let the page show a set that never existed
 * at any single moment.
 *
 * <p>Every figure is scoped to the signed-in user's company. A company without
 * invoices gets zero amounts, zero counts and an empty list — never null.
 *
 * @param outstandingAmount invoiced and sent, not yet paid
 * @param paidThisMonth     paid within the current calendar month
 * @param overdueCount      sent invoices whose due date has passed
 * @param draftCount        invoices not sent yet
 * @param recentInvoices    the most recently touched invoices, newest first
 */
public record DashboardResponse(
        BigDecimal outstandingAmount,
        BigDecimal paidThisMonth,
        long overdueCount,
        long draftCount,
        List<DashboardInvoiceResponse> recentInvoices
) {
}
