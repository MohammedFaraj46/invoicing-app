package io.github.mohammedfaraj46.backend.service;

import io.github.mohammedfaraj46.backend.domain.CreditNoteStatus;

/**
 * Thrown when a credit note is changed or removed after it has been sent.
 * Mapped to HTTP 409: it exists and is yours, its status simply forbids this.
 */
public class CreditNoteNotEditableException extends RuntimeException {

    public CreditNoteNotEditableException(Long id, CreditNoteStatus status) {
        super("Credit note " + id + " has status " + status + " and can no longer be changed");
    }
}
