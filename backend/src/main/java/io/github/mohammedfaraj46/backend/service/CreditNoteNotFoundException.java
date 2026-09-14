package io.github.mohammedfaraj46.backend.service;

/**
 * Thrown when a credit note id does not resolve for the signed-in company —
 * unknown and not-yours share one 404, as everywhere else.
 */
public class CreditNoteNotFoundException extends RuntimeException {

    public CreditNoteNotFoundException(Long id) {
        super("No credit note with id " + id);
    }
}
