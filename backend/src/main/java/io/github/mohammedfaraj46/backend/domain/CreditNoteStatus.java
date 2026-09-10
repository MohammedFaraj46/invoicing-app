package io.github.mohammedfaraj46.backend.domain;

/**
 * Lifecycle of a credit note. Mirrors ck_credit_note_status in V1__init.sql.
 */
public enum CreditNoteStatus {
    DRAFT,
    SENT
}
