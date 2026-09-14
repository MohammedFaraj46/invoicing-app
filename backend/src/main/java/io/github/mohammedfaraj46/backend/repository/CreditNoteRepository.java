package io.github.mohammedfaraj46.backend.repository;

import io.github.mohammedfaraj46.backend.domain.CreditNote;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Credit notes, scoped to their owning company — see the package documentation
 * for the rule.
 */
public interface CreditNoteRepository extends JpaRepository<CreditNote, Long> {

    @EntityGraph(attributePaths = {"lines", "customer", "originalInvoice"})
    Optional<CreditNote> findByIdAndCompanyId(Long id, Long companyId);

    @EntityGraph(attributePaths = {"customer", "originalInvoice"})
    List<CreditNote> findAllByCompanyId(Long companyId, Sort sort);

    /** The credit notes raised against one invoice, still scoped to the owner. */
    @EntityGraph(attributePaths = {"customer", "originalInvoice"})
    List<CreditNote> findAllByCompanyIdAndOriginalInvoiceId(Long companyId, Long invoiceId, Sort sort);

    /**
     * How much of each line of one invoice has already been credited.
     *
     * <p>Counts drafts as well as sent credit notes: a draft is a reservation of
     * credit room, and leaving it out would let someone build up several drafts
     * that each fit but together do not, and then send them all.
     *
     * <p>Stored amounts are negative, so the sums come back negative too; the
     * caller flips the sign.
     *
     * @return one row per credited invoice line: [invoiceLineId, summed net amount]
     */
    @Query("""
            select line.invoiceLine.id, sum(line.lineNetAmount)
            from CreditNoteLine line
            where line.creditNote.originalInvoice.id = :invoiceId
            group by line.invoiceLine.id
            """)
    List<Object[]> sumCreditedNetPerInvoiceLine(@Param("invoiceId") Long invoiceId);

    /** Convenience over {@link #sumCreditedNetPerInvoiceLine} for readability in tests. */
    default BigDecimal creditedNetFor(Long invoiceId, Long invoiceLineId) {
        return sumCreditedNetPerInvoiceLine(invoiceId).stream()
                .filter(row -> invoiceLineId.equals(row[0]))
                .map(row -> (BigDecimal) row[1])
                .findFirst()
                .orElse(BigDecimal.ZERO);
    }
}
