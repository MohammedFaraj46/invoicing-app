package io.github.mohammedfaraj46.backend.service;

/**
 * Thrown when a PDF is requested for an invoice that has no definitive number
 * yet — a draft.
 *
 * <p>A draft is still being worked on: its amounts can change and it has no
 * number, so a PDF of it would be a document that claims to be something it is
 * not. Mapped to HTTP 409, because the invoice exists and is yours; it is simply
 * not far enough along.
 */
public class InvoicePdfUnavailableException extends RuntimeException {

    public InvoicePdfUnavailableException(Long id) {
        super("Invoice " + id + " has no invoice number yet; send it before downloading a PDF");
    }
}
