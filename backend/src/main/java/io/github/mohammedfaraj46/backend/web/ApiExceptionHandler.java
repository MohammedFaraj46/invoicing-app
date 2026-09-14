package io.github.mohammedfaraj46.backend.web;

import io.github.mohammedfaraj46.backend.service.EmailAlreadyUsedException;
import io.github.mohammedfaraj46.backend.security.UnauthenticatedException;
import io.github.mohammedfaraj46.backend.service.CreditNoteNotEditableException;
import io.github.mohammedfaraj46.backend.service.CreditNoteNotFoundException;
import io.github.mohammedfaraj46.backend.service.CreditNotePdfUnavailableException;
import io.github.mohammedfaraj46.backend.service.CreditNoteStatusTransitionException;
import io.github.mohammedfaraj46.backend.service.CustomerInUseException;
import io.github.mohammedfaraj46.backend.service.CustomerNotFoundException;
import io.github.mohammedfaraj46.backend.service.InvalidCredentialsException;
import io.github.mohammedfaraj46.backend.service.InvalidStatusTransitionException;
import io.github.mohammedfaraj46.backend.service.InvoiceNotEditableException;
import io.github.mohammedfaraj46.backend.service.InvoiceValidationException;
import io.github.mohammedfaraj46.backend.service.InvoiceNotFoundException;
import io.github.mohammedfaraj46.backend.service.InvoicePdfUnavailableException;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Turns failures into consistent HTTP responses.
 *
 * <p>Every error the API produces is problem+json with the same fields, whether
 * it comes from a service, from bean validation, or from Spring itself.
 * Extending {@link ResponseEntityExceptionHandler} is what covers the last
 * group: a malformed JSON body, an id that is not a number, an unknown path and
 * a wrong HTTP method used to come back in the default
 * {@code timestamp/status/error/path} shape, which a client cannot read the same
 * way as the rest.
 *
 * <p>The status codes follow one rule throughout. 400 when the request itself is
 * wrong, 401 when nobody is signed in, 404 when an id does not resolve within
 * the signed-in company - whether it is unknown or someone elses - and 409 when
 * the request is well formed and the data is yours, but its current state
 * forbids the change.
 */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    /** Unique index on app_user.email, see V1__init.sql. */
    private static final String EMAIL_UNIQUE_CONSTRAINT = "uq_user_email";

    /** SQLSTATE 23505: unique violation. */
    private static final String SQLSTATE_UNIQUE_VIOLATION = "23505";

    /** SQLSTATE 23503: foreign key violation, a row is still referenced. */
    private static final String SQLSTATE_FOREIGN_KEY_VIOLATION = "23503";

    /**
     * 400 with one entry per rejected field. Nested fields keep their path, so a
     * bad company name shows up as "company.name".
     */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException exception, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {

        Map<String, String> errors = new LinkedHashMap<>();
        exception.getBindingResult().getFieldErrors().forEach(error ->
                errors.putIfAbsent(error.getField(), error.getDefaultMessage()));
        exception.getBindingResult().getGlobalErrors().forEach(error ->
                errors.putIfAbsent(error.getObjectName(), error.getDefaultMessage()));

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, "Validation failed");
        problem.setTitle("Validation failed");
        problem.setProperty("errors", errors);

        // overridden rather than left to the base class, which produces a detail
        // string instead of the per-field map the forms put next to their inputs
        return handleExceptionInternal(exception, problem, headers, HttpStatus.BAD_REQUEST, request);
    }

    /**
     * 404 both for an id that does not exist and for one owned by another
     * company, so the response cannot be used to map out which ids are taken.
     */
    @ExceptionHandler(CustomerNotFoundException.class)
    public ProblemDetail handleCustomerNotFound(CustomerNotFoundException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.NOT_FOUND, exception.getMessage());
        problem.setTitle("Customer not found");
        return problem;
    }

    /**
     * 409 rather than 404: the customer is yours and exists, but invoices still
     * name it, and an invoice may not lose who it was sent to.
     */
    @ExceptionHandler(CustomerInUseException.class)
    public ProblemDetail handleCustomerInUse(CustomerInUseException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT, exception.getMessage());
        problem.setTitle("Customer still has invoices");
        return problem;
    }

    /**
     * A domain rule was broken by a payload that bean validation let through —
     * a VAT type that does not match its rate. Answered in the same 400 shape as
     * bean validation, so forms can treat both alike.
     */
    @ExceptionHandler(InvoiceValidationException.class)
    public ProblemDetail handleInvoiceValidation(InvoiceValidationException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, "Validation failed");
        problem.setTitle("Validation failed");
        problem.setProperty("errors", exception.getFieldErrors());
        return problem;
    }

    /** Same reasoning as a missing customer: unknown and not-yours share one 404. */
    @ExceptionHandler(InvoiceNotFoundException.class)
    public ProblemDetail handleInvoiceNotFound(InvoiceNotFoundException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.NOT_FOUND, exception.getMessage());
        problem.setTitle("Invoice not found");
        return problem;
    }

    /**
     * 409 rather than 404: the invoice is yours and exists, but its status no
     * longer allows the change.
     */
    @ExceptionHandler(InvoiceNotEditableException.class)
    public ProblemDetail handleInvoiceNotEditable(InvoiceNotEditableException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT, exception.getMessage());
        problem.setTitle("Invoice can no longer be changed");
        return problem;
    }

    /**
     * 409: the invoice exists and is yours, but its lifecycle does not allow the
     * status change that was asked for.
     */
    @ExceptionHandler(InvalidStatusTransitionException.class)
    public ProblemDetail handleInvalidStatusTransition(InvalidStatusTransitionException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT, exception.getMessage());
        problem.setTitle("Status transition not allowed");
        return problem;
    }

    /** 409: a draft has no number, so there is no invoice to put in a PDF yet. */
    @ExceptionHandler(InvoicePdfUnavailableException.class)
    public ProblemDetail handleInvoicePdfUnavailable(InvoicePdfUnavailableException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT, exception.getMessage());
        problem.setTitle("No PDF for a draft");
        return problem;
    }

    /** Unknown and not-yours share one 404, as with invoices and customers. */
    @ExceptionHandler(CreditNoteNotFoundException.class)
    public ProblemDetail handleCreditNoteNotFound(CreditNoteNotFoundException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.NOT_FOUND, exception.getMessage());
        problem.setTitle("Credit note not found");
        return problem;
    }

    /** 409: the credit note is yours, its status simply forbids the change. */
    @ExceptionHandler({CreditNoteNotEditableException.class, CreditNoteStatusTransitionException.class})
    public ProblemDetail handleCreditNoteConflict(RuntimeException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT, exception.getMessage());
        problem.setTitle("Credit note can no longer be changed");
        return problem;
    }

    /** 409: a draft credit note has no number, so there is nothing to put in a PDF. */
    @ExceptionHandler(CreditNotePdfUnavailableException.class)
    public ProblemDetail handleCreditNotePdfUnavailable(CreditNotePdfUnavailableException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT, exception.getMessage());
        problem.setTitle("No PDF for a draft");
        return problem;
    }

    /**
     * A service asked who is signed in and nobody was. The filter chain normally
     * stops this earlier, so reaching here means an endpoint is not protected.
     */
    @ExceptionHandler(UnauthenticatedException.class)
    public ProblemDetail handleUnauthenticated(UnauthenticatedException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.UNAUTHORIZED, "Authentication required");
        problem.setTitle("Unauthorized");
        return problem;
    }

    /**
     * 401 with the same message whichever half was wrong, so the response cannot
     * be used to discover which email addresses have an account.
     */
    @ExceptionHandler(InvalidCredentialsException.class)
    public ProblemDetail handleInvalidCredentials(InvalidCredentialsException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.UNAUTHORIZED, "Invalid email or password");
        problem.setTitle("Authentication failed");
        return problem;
    }

    @ExceptionHandler(EmailAlreadyUsedException.class)
    public ProblemDetail handleEmailAlreadyUsed(EmailAlreadyUsedException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT, exception.getMessage());
        problem.setTitle("Email already registered");
        return problem;
    }

    /**
     * The database refused a write that the service had already checked. In
     * practice that means a race: two registrations for the same address, or a
     * row deleted just as something started referencing it. The answer in both
     * cases is that the world moved, which is a 409 - not a 500, and not a stack
     * trace.
     *
     * <p>Recognised by SQLSTATE rather than by message text, so it holds for any
     * constraint. The message itself never reaches the caller: constraint names
     * and column values are internals.
     *
     * <p>Anything else under this exception type is a genuine bug, a NOT NULL we
     * forgot say, and is rethrown so it becomes a logged 500.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail handleDataIntegrityViolation(DataIntegrityViolationException exception) {
        String sqlState = sqlStateOf(exception);

        if (SQLSTATE_UNIQUE_VIOLATION.equals(sqlState)) {
            // the one unique index a user can hit on purpose deserves its own wording
            boolean email = mentionsEmailConstraint(exception);
            log.warn("Unique constraint rejected a write ({})",
                    email ? "duplicate email" : "other unique index");

            ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
                    email ? "An account with this email already exists"
                          : "This conflicts with something that already exists");
            problem.setTitle(email ? "Email already registered" : "Conflict");
            return problem;
        }

        if (SQLSTATE_FOREIGN_KEY_VIOLATION.equals(sqlState)) {
            log.warn("Foreign key rejected a write; something still references the row");
            ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
                    "This is still referenced by other data and cannot be changed");
            problem.setTitle("Still referenced");
            return problem;
        }

        throw exception;
    }

    /**
     * Last resort. Everything not recognised above is a fault on our side, so it
     * is logged in full and answered with a body carrying nothing from the
     * exception - no message, no stack trace, no SQL.
     *
     * <p>The reference is the one thing that crosses over: it lets a support
     * question ("I got error 4f3a9c2e") be matched to the log line that has the
     * detail.
     */
    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception exception) {
        String reference = UUID.randomUUID().toString().substring(0, 8);
        log.error("Unhandled exception [{}]", reference, exception);

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR,
                "Something went wrong on our side. Reference: " + reference);
        problem.setTitle("Internal server error");
        problem.setProperty("reference", reference);
        return problem;
    }

    /** The SQLSTATE of the underlying database error, if there is one. */
    private static String sqlStateOf(Throwable throwable) {
        for (Throwable cause = throwable; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sqlException) {
                return sqlException.getSQLState();
            }
            if (cause.getCause() == cause) {
                break;
            }
        }
        return null;
    }

    private static boolean mentionsEmailConstraint(Throwable throwable) {
        for (Throwable cause = throwable; cause != null; cause = cause.getCause()) {
            String message = cause.getMessage();
            if (message != null && message.contains(EMAIL_UNIQUE_CONSTRAINT)) {
                return true;
            }
            if (cause.getCause() == cause) {
                break;
            }
        }
        return false;
    }
}
