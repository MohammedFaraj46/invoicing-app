package io.github.mohammedfaraj46.backend.repository;

import io.github.mohammedfaraj46.backend.domain.Invoice;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

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
}
