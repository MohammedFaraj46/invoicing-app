package io.github.mohammedfaraj46.backend.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The credit note lifecycle, in the same shape as {@link InvoiceStatusTest}.
 *
 * <p>Its integration tests exercise the transitions over HTTP, but the enum owns
 * the rule and deserves to be pinned down on its own: this is where a later
 * "just add a CANCELLED status" would quietly widen what is allowed.
 */
@DisplayName("CreditNoteStatus")
class CreditNoteStatusTest {

    @Test
    @DisplayName("DRAFT to SENT is the only transition there is")
    void onlyForwardTransition() {
        assertThat(CreditNoteStatus.DRAFT.canTransitionTo(CreditNoteStatus.SENT)).isTrue();

        assertThat(CreditNoteStatus.SENT.canTransitionTo(CreditNoteStatus.DRAFT)).isFalse();
    }

    @Test
    @DisplayName("SENT is final")
    void sentIsFinal() {
        for (CreditNoteStatus target : CreditNoteStatus.values()) {
            assertThat(CreditNoteStatus.SENT.canTransitionTo(target))
                    .as("SENT to %s", target)
                    .isFalse();
        }
    }

    @Test
    @DisplayName("no status can transition to itself")
    void noSelfTransition() {
        for (CreditNoteStatus status : CreditNoteStatus.values()) {
            assertThat(status.canTransitionTo(status))
                    .as("%s to itself", status)
                    .isFalse();
        }
    }

    @Test
    @DisplayName("only a draft is editable")
    void onlyDraftIsEditable() {
        assertThat(CreditNoteStatus.DRAFT.isEditable()).isTrue();
        assertThat(CreditNoteStatus.SENT.isEditable()).isFalse();
    }

    @Test
    @DisplayName("there are exactly two statuses")
    void twoStatuses() {
        // a third one would need its own transitions and its own decision about
        // editability; this fails the moment someone adds it without doing that
        assertThat(CreditNoteStatus.values())
                .containsExactly(CreditNoteStatus.DRAFT, CreditNoteStatus.SENT);
    }
}
