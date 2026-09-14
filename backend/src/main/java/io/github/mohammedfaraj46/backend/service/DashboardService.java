package io.github.mohammedfaraj46.backend.service;

import io.github.mohammedfaraj46.backend.domain.Invoice;
import io.github.mohammedfaraj46.backend.domain.InvoiceStatus;
import io.github.mohammedfaraj46.backend.repository.InvoiceRepository;
import io.github.mohammedfaraj46.backend.security.CurrentUser;
import io.github.mohammedfaraj46.backend.service.calculation.InvoiceCalculator;
import io.github.mohammedfaraj46.backend.web.dto.DashboardInvoiceResponse;
import io.github.mohammedfaraj46.backend.web.dto.DashboardResponse;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The figures on the dashboard.
 *
 * <p>Each figure is its own aggregate query, filtered on the company from
 * {@link CurrentUser}. Loading every invoice and counting in Java would give the
 * same answer today and a slow page later; letting the database sum and count
 * keeps the work proportional to the answer rather than to the archive.
 *
 * <p>Read-only and in one transaction, so the four figures and the recent list
 * describe the same moment.
 */
@Service
public class DashboardService {

    /** How many invoices the "recent" list shows. */
    private static final int RECENT_INVOICE_COUNT = 5;

    /**
     * Newest first. The id breaks ties, so two invoices saved within the same
     * clock tick still come back in a stable order.
     */
    private static final Sort MOST_RECENT_FIRST =
            Sort.by(Sort.Order.desc("updatedAt"), Sort.Order.desc("id"));

    private final InvoiceRepository invoiceRepository;
    private final CurrentUser currentUser;

    public DashboardService(InvoiceRepository invoiceRepository, CurrentUser currentUser) {
        this.invoiceRepository = invoiceRepository;
        this.currentUser = currentUser;
    }

    @Transactional(readOnly = true)
    public DashboardResponse overview() {
        Long companyId = currentUser.companyId();
        LocalDate today = LocalDate.now();

        List<Invoice> recent = invoiceRepository.findAllByCompanyId(
                companyId, PageRequest.of(0, RECENT_INVOICE_COUNT, MOST_RECENT_FIRST));

        return new DashboardResponse(
                InvoiceCalculator.toMoney(
                        invoiceRepository.sumTotalAmountByStatus(companyId, InvoiceStatus.SENT)),
                InvoiceCalculator.toMoney(paidThisMonth(companyId, today)),
                invoiceRepository.countByCompanyIdAndStatusAndDueDateBefore(
                        companyId, InvoiceStatus.SENT, today),
                invoiceRepository.countByCompanyIdAndStatus(companyId, InvoiceStatus.DRAFT),
                recent.stream().map(DashboardInvoiceResponse::from).toList());
    }

    /**
     * Everything paid since the first of this month.
     *
     * <p>{@code paidAt} is an instant while "this month" is a wall-clock notion,
     * so the month boundaries are resolved in the server's own zone. The app is
     * single-country; when it stops being that, the company's zone belongs here
     * instead.
     */
    private BigDecimal paidThisMonth(Long companyId, LocalDate today) {
        ZoneId zone = ZoneId.systemDefault();
        LocalDate firstOfMonth = today.withDayOfMonth(1);

        OffsetDateTime from = firstOfMonth.atStartOfDay(zone).toOffsetDateTime();
        OffsetDateTime until = firstOfMonth.plusMonths(1).atStartOfDay(zone).toOffsetDateTime();

        return invoiceRepository.sumTotalAmountPaidBetween(companyId, InvoiceStatus.PAID, from, until);
    }
}
