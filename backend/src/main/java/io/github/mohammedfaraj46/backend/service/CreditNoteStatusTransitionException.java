package io.github.mohammedfaraj46.backend.service;

import io.github.mohammedfaraj46.backend.domain.CreditNoteStatus;

/**
 * Thrown when a credit note is asked to move somewhere its lifecycle does not
 * allow — sending one that is already sent. Mapped to HTTP 409.
 */
public class CreditNoteStatusTransitionException extends RuntimeException {

    public CreditNoteStatusTransitionException(Long id, CreditNoteStatus from, CreditNoteStatus to) {
        super(from == to
                ? "Credit note " + id + " already has status " + from
                : "Credit note " + id + " cannot go from " + from + " to " + to);
    }
}
