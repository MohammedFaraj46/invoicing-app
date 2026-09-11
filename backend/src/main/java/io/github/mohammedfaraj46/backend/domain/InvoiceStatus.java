package io.github.mohammedfaraj46.backend.domain;

import java.util.Map;
import java.util.Set;

/**
 * Lifecycle of an invoice. Mirrors ck_invoice_status in V1__init.sql.
 *
 * <p>The enum owns its own transition rules, so there is one answer to "may this
 * invoice go there" and no service can invent a shortcut:
 *
 * <pre>
 *   DRAFT ──send──▶ SENT ──pay──▶ PAID
 * </pre>
 *
 * <p>Only forward moves are allowed and PAID is final: un-paying an invoice
 * would mean rewriting history on a document the customer already has. If that
 * is ever needed it should be a correction with its own trail, not a status flip.
 *
 * <p>OVERDUE is in the table because the database column allows it, but nothing
 * reaches it yet — the scheduled task that moves SENT to OVERDUE comes later, and
 * will add {@code SENT -> OVERDUE} and {@code OVERDUE -> PAID} here.
 */
public enum InvoiceStatus {
    DRAFT,
    SENT,
    PAID,
    OVERDUE;

    private static final Map<InvoiceStatus, Set<InvoiceStatus>> ALLOWED_TRANSITIONS = Map.of(
            DRAFT, Set.of(SENT),
            SENT, Set.of(PAID),
            PAID, Set.of(),
            OVERDUE, Set.of());

    /** Whether this invoice may still move to {@code target}. */
    public boolean canTransitionTo(InvoiceStatus target) {
        return ALLOWED_TRANSITIONS.get(this).contains(target);
    }

    /**
     * Whether the lines and amounts may still be changed. Only a draft may: once
     * an invoice has been sent, its content is a snapshot the customer holds too.
     */
    public boolean isEditable() {
        return this == DRAFT;
    }
}
