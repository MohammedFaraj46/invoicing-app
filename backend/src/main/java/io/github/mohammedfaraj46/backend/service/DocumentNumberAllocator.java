package io.github.mohammedfaraj46.backend.service;

import io.github.mohammedfaraj46.backend.domain.DocumentType;
import io.github.mohammedfaraj46.backend.domain.NumberSequence;
import io.github.mohammedfaraj46.backend.repository.NumberSequenceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Hands out the definitive number for a document, sequentially and without
 * duplicates.
 *
 * <p>How it stays safe under concurrency:
 * <ol>
 *   <li>make sure the counter row for (company, type, year) exists, racelessly</li>
 *   <li>read it with {@code SELECT ... FOR UPDATE}, which blocks any other sender
 *       for this company and year until this transaction ends</li>
 *   <li>increment it and build the number</li>
 * </ol>
 *
 * <p>{@link Propagation#MANDATORY} on purpose: the lock is only worth anything
 * for as long as the surrounding transaction lives. Called without one, this
 * fails loudly instead of silently handing out a number under a lock that is
 * released a microsecond later.
 *
 * <p>Credit notes draw from their own series through the same method, by passing
 * {@link DocumentType#CREDIT_NOTE}.
 */
@Service
public class DocumentNumberAllocator {

    /** 2026-0001; four digits covers 9999 documents per year. */
    private static final String NUMBER_FORMAT = "%d-%04d";

    private final NumberSequenceRepository numberSequenceRepository;

    public DocumentNumberAllocator(NumberSequenceRepository numberSequenceRepository) {
        this.numberSequenceRepository = numberSequenceRepository;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public String allocate(Long companyId, DocumentType documentType, int year) {
        numberSequenceRepository.insertIfAbsent(companyId, documentType.name(), year);

        NumberSequence sequence = numberSequenceRepository
                .findByCompanyIdAndDocumentTypeAndYear(companyId, documentType, year)
                .orElseThrow(() -> new IllegalStateException(
                        "Number sequence for company " + companyId + ", " + documentType
                                + " and year " + year + " is missing right after it was created"));

        int next = sequence.next();
        return NUMBER_FORMAT.formatted(year, next);
    }
}
