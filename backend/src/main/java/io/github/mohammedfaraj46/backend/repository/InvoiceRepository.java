package io.github.mohammedfaraj46.backend.repository;

import io.github.mohammedfaraj46.backend.domain.Invoice;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
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
