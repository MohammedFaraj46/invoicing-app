package io.github.mohammedfaraj46.backend.service;

import io.github.mohammedfaraj46.backend.domain.InvoiceStatus;

/**
 * Thrown when an invoice is changed or removed while its status no longer
 * allows it. Only a DRAFT may be edited or deleted; once sent, the lines and
 * amounts are fixed.
 *
 * <p>Mapped to HTTP 409: the invoice exists and you own it, it is simply in a
 * state that forbids the operation.
 */
public class InvoiceNotEditableException extends RuntimeException {

    public InvoiceNotEditableException(Long id, InvoiceStatus status) {
        super("Invoice " + id + " has status " + status + " and can no longer be changed");
    }
}
