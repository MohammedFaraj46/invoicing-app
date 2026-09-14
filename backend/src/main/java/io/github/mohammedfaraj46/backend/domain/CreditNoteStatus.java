package io.github.mohammedfaraj46.backend.domain;

import java.util.Map;
import java.util.Set;

/**
 * Lifecycle of a credit note. Mirrors ck_credit_note_status in V1__init.sql.
 *
 * <p>Like an invoice, the enum owns its own transition rules:
 *
 * <pre>
 *   DRAFT --send--> SENT
 * </pre>
 *
 * <p>SENT is final. A credit note that has gone out has a number and corrects a
 * document the customer already holds; undoing it would mean rewriting history.
 */
public enum CreditNoteStatus {
    DRAFT,
    SENT;

    private static final Map<CreditNoteStatus, Set<CreditNoteStatus>> ALLOWED_TRANSITIONS = Map.of(
            DRAFT, Set.of(SENT),
            SENT, Set.of());

    public boolean canTransitionTo(CreditNoteStatus target) {
        return ALLOWED_TRANSITIONS.get(this).contains(target);
    }

    /** Only a draft may still have its lines and amounts changed or be removed. */
    public boolean isEditable() {
        return this == DRAFT;
    }
}
