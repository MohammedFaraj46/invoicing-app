package io.github.mohammedfaraj46.backend.service;

/**
 * Thrown when a PDF is requested for a credit note that has no number yet.
 * Mapped to HTTP 409, the same as for a draft invoice.
 */
public class CreditNotePdfUnavailableException extends RuntimeException {

    public CreditNotePdfUnavailableException(Long id) {
        super("Credit note " + id + " has no number yet; send it before downloading a PDF");
    }
}
