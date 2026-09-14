package io.github.mohammedfaraj46.backend.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.SQLException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

/**
 * The two handlers that exist so a database or runtime failure never reaches the
 * caller as itself.
 *
 * <p>Unit tests rather than integration tests on purpose: the whole point is
 * what happens with an exception the application does not produce on request,
 * and constructing one directly is the only honest way to try it.
 */
@DisplayName("ApiExceptionHandler")
class ApiExceptionHandlerTest {

    private final ApiExceptionHandler handler = new ApiExceptionHandler();

    @Test
    @DisplayName("an unexpected exception yields a 500 that says nothing about it")
    void unexpectedExceptionLeaksNothing() {
        RuntimeException exception = new IllegalStateException(
                "could not execute statement: select password_hash from app_user where id = 3");

        ProblemDetail problem = handler.handleUnexpected(exception);

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR.value());
        assertThat(problem.getDetail())
                .doesNotContain("select")
                .doesNotContain("password_hash")
                .doesNotContain("IllegalStateException");
        // a reference the caller can quote, matching the logged line
        assertThat(problem.getProperties()).containsKey("reference");
        assertThat(problem.getDetail()).contains(String.valueOf(problem.getProperties().get("reference")));
    }

    @Test
    @DisplayName("each unexpected exception gets its own reference")
    void referencesAreUnique() {
        String first = String.valueOf(handler.handleUnexpected(new RuntimeException())
                .getProperties().get("reference"));
        String second = String.valueOf(handler.handleUnexpected(new RuntimeException())
                .getProperties().get("reference"));

        assertThat(first).isNotEqualTo(second);
    }

    @Test
    @DisplayName("a unique violation becomes a 409 without the constraint name")
    void uniqueViolationBecomesConflict() {
        ProblemDetail problem = handler.handleDataIntegrityViolation(
                violation("23505", "duplicate key value violates unique constraint \"uq_something\""));

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
        assertThat(problem.getDetail()).doesNotContain("uq_something").doesNotContain("duplicate key");
    }

    @Test
    @DisplayName("the duplicate email case keeps its own wording")
    void duplicateEmailIsNamed() {
        ProblemDetail problem = handler.handleDataIntegrityViolation(
                violation("23505", "duplicate key value violates unique constraint \"uq_user_email\""));

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
        assertThat(problem.getDetail()).contains("email");
    }

    @Test
    @DisplayName("a foreign key violation becomes a 409, not a 500")
    void foreignKeyViolationBecomesConflict() {
        ProblemDetail problem = handler.handleDataIntegrityViolation(
                violation("23503", "update or delete on table \"customer\" violates foreign key"));

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
        assertThat(problem.getDetail()).doesNotContain("customer").doesNotContain("foreign key");
    }

    @Test
    @DisplayName("an integrity failure that is really a bug is rethrown, so it becomes a 500")
    void otherIntegrityFailuresAreRethrown() {
        // 23502 is a NOT NULL violation: nothing the caller did, a column we
        // forgot to fill. It should surface as a fault, not as a polite conflict.
        DataIntegrityViolationException exception =
                violation("23502", "null value in column \"total_amount\" violates not-null constraint");

        assertThatThrownBy(() -> handler.handleDataIntegrityViolation(exception))
                .isSameAs(exception);
    }

    private static DataIntegrityViolationException violation(String sqlState, String message) {
        return new DataIntegrityViolationException(
                "could not execute statement", new SQLException(message, sqlState));
    }
}
