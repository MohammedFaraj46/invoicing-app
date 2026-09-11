package io.github.mohammedfaraj46.backend.service;

import io.github.mohammedfaraj46.backend.domain.InvoiceStatus;

/**
 * Thrown when an invoice is asked to move to a status its lifecycle does not
 * allow — paying a draft, sending an invoice twice, un-paying a paid one.
 *
 * <p>Mapped to HTTP 409: the invoice exists and you own it, it is simply not
 * where it would have to be for this to make sense.
 */
public class InvalidStatusTransitionException extends RuntimeException {

    public InvalidStatusTransitionException(Long id, InvoiceStatus from, InvoiceStatus to) {
        super(from == to
                ? "Invoice " + id + " already has status " + from
                : "Invoice " + id + " cannot go from " + from + " to " + to);
    }
}
