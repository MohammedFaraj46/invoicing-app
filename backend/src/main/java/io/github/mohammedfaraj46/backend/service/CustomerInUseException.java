package io.github.mohammedfaraj46.backend.service;

/**
 * Thrown when a customer cannot be removed because invoices still point at it.
 *
 * <p>An invoice names who it was sent to, and that has to stay true: deleting
 * the customer would either take the invoices with it or leave them pointing at
 * nothing, and an invoice that has been sent is not ours to rewrite. The
 * database enforces the same thing with a foreign key; this exception is what
 * turns it into an answer the caller can act on.
 */
public class CustomerInUseException extends RuntimeException {

    public CustomerInUseException(Long id) {
        super("Customer " + id + " still has invoices and cannot be deleted");
    }
}
