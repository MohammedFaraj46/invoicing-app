package io.github.mohammedfaraj46.backend.repository;

import io.github.mohammedfaraj46.backend.domain.DocumentType;
import io.github.mohammedfaraj46.backend.domain.NumberSequence;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * The counters behind invoice and credit note numbering.
 *
 * <p>Not an ownership-filtered repository in the usual sense: callers never pass
 * an id from a request, they pass the company id from the signed-in user.
 */
public interface NumberSequenceRepository extends JpaRepository<NumberSequence, Long> {

    /**
     * Reads the counter row and holds it until the transaction ends
     * ({@code SELECT ... FOR UPDATE}). Two simultaneous sends queue up here, so
     * they cannot read the same {@code last_value} and hand out the same number.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<NumberSequence> findByCompanyIdAndDocumentTypeAndYear(
            Long companyId, DocumentType documentType, int year);

    /**
     * Creates the row for a (company, type, year) that has none yet, doing
     * nothing if it is already there.
     *
     * <p>A plain "look, then insert" has a race: two senders both find nothing
     * and both insert, and one hits the unique constraint — which in JPA poisons
     * the whole transaction. {@code ON CONFLICT DO NOTHING} pushes that race down
     * to the database, where the loser simply waits for the winner to commit and
     * then finds the row waiting for it.
     */
    @Modifying(flushAutomatically = true)
    @Query(value = """
            INSERT INTO number_sequence (company_id, document_type, year, last_value)
            VALUES (:companyId, :documentType, :year, 0)
            ON CONFLICT (company_id, document_type, year) DO NOTHING
            """, nativeQuery = true)
    void insertIfAbsent(@Param("companyId") Long companyId,
                        @Param("documentType") String documentType,
                        @Param("year") int year);
}
