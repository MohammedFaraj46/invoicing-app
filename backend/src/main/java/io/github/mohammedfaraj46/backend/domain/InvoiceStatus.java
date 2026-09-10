package io.github.mohammedfaraj46.backend.domain;

/**
 * Lifecycle of an invoice. Mirrors ck_invoice_status in V1__init.sql.
 */
public enum InvoiceStatus {
    DRAFT,
    SENT,
    PAID,
    OVERDUE
}
