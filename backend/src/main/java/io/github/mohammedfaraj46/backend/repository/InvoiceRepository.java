package io.github.mohammedfaraj46.backend.repository;

import io.github.mohammedfaraj46.backend.domain.Invoice;
import io.github.mohammedfaraj46.backend.domain.InvoiceStatus;
import jakarta.persistence.LockModeType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Invoices, scoped to their owning company — see the package documentation for
 * the rule.
 *
 * <p>There is no repository for the lines: they belong to the invoice and are
 * saved, replaced and deleted through it, so the invoice's own ownership filter
 * covers them too.
 */
public interface InvoiceRepository extends JpaRepository<Invoice, Long> {

    /** Empty for an invoice of another company, exactly as for one that does not exist. */
    @EntityGraph(attributePaths = {"lines", "customer"})
    Optional<Invoice> findByIdAndCompanyId(Long id, Long companyId);

    /** The list view needs the customer name but not the lines. */
    @EntityGraph(attributePaths = "customer")
    List<Invoice> findAllByCompanyId(Long companyId, Sort sort);

    boolean existsByIdAndCompanyId(Long id, Long companyId);

    /**
     * A page of the company's invoices in the order the caller asks for. Used by
     * the dashboard to take only the few most recent ones, so the database does
     * the sorting and the limiting rather than Java.
     */
    @EntityGraph(attributePaths = "customer")
    List<Invoice> findAllByCompanyId(Long companyId, Pageable pageable);

    /**
     * One customer's invoices. Filtered on the company as well as the customer:
     * the customer id is checked for ownership before this runs, and keeping the
     * company in the query means a mistake there still cannot widen the result.
     */
    @EntityGraph(attributePaths = "customer")
    List<Invoice> findAllByCompanyIdAndCustomerId(Long companyId, Long customerId, Sort sort);

    boolean existsByCompanyIdAndCustomerId(Long companyId, Long customerId);

    long countByCompanyIdAndStatus(Long companyId, InvoiceStatus status);

    /**
     * Invoices that are still out and whose due date has passed. Computed from
     * the date rather than read from the status: OVERDUE is not assigned yet, so
     * "late" is a question about today, not a stored fact.
     */
    long countByCompanyIdAndStatusAndDueDateBefore(Long companyId, InvoiceStatus status, LocalDate date);

    /**
     * Total invoiced in one status, as a single aggregate. Null when the company
     * has no invoice in that status — SQL sums nothing to nothing — which the
     * caller turns into a zero amount.
     */
    @Query("""
            select sum(invoice.totalAmount) from Invoice invoice
            where invoice.company.id = :companyId
              and invoice.status = :status
            """)
    BigDecimal sumTotalAmountByStatus(@Param("companyId") Long companyId,
                                      @Param("status") InvoiceStatus status);

    /**
     * Total paid within a half-open window [from, until). Half-open rather than
     * BETWEEN so a payment at midnight on the first of the month belongs to
     * exactly one month.
     */
    /** One customer's total in a single status; null when there is none. */
    @Query("""
            select sum(invoice.totalAmount) from Invoice invoice
            where invoice.company.id = :companyId
              and invoice.customer.id = :customerId
              and invoice.status = :status
            """)
    BigDecimal sumTotalAmountByCustomerAndStatus(@Param("companyId") Long companyId,
                                                 @Param("customerId") Long customerId,
                                                 @Param("status") InvoiceStatus status);

    /**
     * One customer's total in every status but one. Used with DRAFT excluded:
     * a draft has no number and was never sent, so it is not yet invoiced.
     */
    @Query("""
            select sum(invoice.totalAmount) from Invoice invoice
            where invoice.company.id = :companyId
              and invoice.customer.id = :customerId
              and invoice.status <> :excludedStatus
            """)
    BigDecimal sumTotalAmountByCustomerExcludingStatus(@Param("companyId") Long companyId,
                                                       @Param("customerId") Long customerId,
                                                       @Param("excludedStatus") InvoiceStatus excludedStatus);

    @Query("""
            select sum(invoice.totalAmount) from Invoice invoice
            where invoice.company.id = :companyId
              and invoice.status = :status
              and invoice.paidAt >= :from
              and invoice.paidAt < :until
            """)
    BigDecimal sumTotalAmountPaidBetween(@Param("companyId") Long companyId,
                                         @Param("status") InvoiceStatus status,
                                         @Param("from") OffsetDateTime from,
                                         @Param("until") OffsetDateTime until);

    /**
     * The same ownership-filtered lookup, but holding the invoice row until the
     * transaction ends ({@code SELECT ... FOR UPDATE}).
     *
     * <p>Used when creating a credit note: the check "does this fit in what is
     * left to credit" reads the existing credit notes and then writes a new one,
     * and two of those running at once would both see the same room and both
     * take it. Locking the invoice serialises crediting per invoice, the same
     * way the number sequence row serialises numbering.
     *
     * <p>Deliberately fetches no collections: PostgreSQL refuses
     * {@code FOR UPDATE} on the nullable side of an outer join, which is what an
     * entity graph over the lines would produce. The lines are loaded after.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select invoice from Invoice invoice where invoice.id = :id and invoice.company.id = :companyId")
    Optional<Invoice> lockByIdAndCompanyId(@Param("id") Long id, @Param("companyId") Long companyId);
}
