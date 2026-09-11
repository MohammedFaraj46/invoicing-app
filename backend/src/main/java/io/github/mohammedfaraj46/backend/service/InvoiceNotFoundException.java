package io.github.mohammedfaraj46.backend.service;

/**
 * Thrown when an invoice id does not resolve for the signed-in company —
 * whether because it does not exist, or because it belongs to someone else.
 * Both cases share one 404, so the response cannot be used to map out which ids
 * are taken.
 */
public class InvoiceNotFoundException extends RuntimeException {

    public InvoiceNotFoundException(Long id) {
        super("No invoice with id " + id);
    }
}
