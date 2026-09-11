package io.github.mohammedfaraj46.backend.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * The transition table, checked exhaustively and without a database: for every
 * pair of statuses, exactly the two forward moves are allowed.
 */
@DisplayName("InvoiceStatus")
class InvoiceStatusTest {

    /** The complete set of moves the application permits today. */
    private static final Set<String> ALLOWED = Set.of("DRAFT->SENT", "SENT->PAID");

    @ParameterizedTest
    @EnumSource(InvoiceStatus.class)
    @DisplayName("only the two forward transitions are allowed, from any status")
    void onlyForwardTransitionsAreAllowed(InvoiceStatus from) {
        for (InvoiceStatus to : InvoiceStatus.values()) {
            boolean expected = ALLOWED.contains(from + "->" + to);
            assertThat(from.canTransitionTo(to))
                    .as("%s -> %s", from, to)
                    .isEqualTo(expected);
        }
    }

    @Test
    @DisplayName("PAID is final")
    void paidIsFinal() {
        for (InvoiceStatus to : InvoiceStatus.values()) {
            assertThat(InvoiceStatus.PAID.canTransitionTo(to)).as("PAID -> %s", to).isFalse();
        }
    }

    @Test
    @DisplayName("no status can transition to itself")
    void noSelfTransitions() {
        for (InvoiceStatus status : InvoiceStatus.values()) {
            assertThat(status.canTransitionTo(status)).as("%s -> itself", status).isFalse();
        }
    }

    @Test
    @DisplayName("only a draft is editable")
    void onlyDraftIsEditable() {
        assertThat(InvoiceStatus.DRAFT.isEditable()).isTrue();
        assertThat(InvoiceStatus.SENT.isEditable()).isFalse();
        assertThat(InvoiceStatus.PAID.isEditable()).isFalse();
        assertThat(InvoiceStatus.OVERDUE.isEditable()).isFalse();
    }

    @Test
    @DisplayName("OVERDUE is not reachable yet")
    void overdueIsNotReachableYet() {
        // the scheduled task that introduces SENT -> OVERDUE comes later
        for (InvoiceStatus from : InvoiceStatus.values()) {
            assertThat(from.canTransitionTo(InvoiceStatus.OVERDUE))
                    .as("%s -> OVERDUE", from)
                    .isFalse();
        }
    }
}
