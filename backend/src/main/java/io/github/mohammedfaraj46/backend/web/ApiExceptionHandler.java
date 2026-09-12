package io.github.mohammedfaraj46.backend.web;

import io.github.mohammedfaraj46.backend.service.EmailAlreadyUsedException;
import io.github.mohammedfaraj46.backend.security.UnauthenticatedException;
import io.github.mohammedfaraj46.backend.service.CustomerNotFoundException;
import io.github.mohammedfaraj46.backend.service.InvalidCredentialsException;
import io.github.mohammedfaraj46.backend.service.InvalidStatusTransitionException;
import io.github.mohammedfaraj46.backend.service.InvoiceNotEditableException;
import io.github.mohammedfaraj46.backend.service.InvoiceValidationException;
import io.github.mohammedfaraj46.backend.service.InvoiceNotFoundException;
import io.github.mohammedfaraj46.backend.service.InvoicePdfUnavailableException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Turns service and validation failures into consistent HTTP responses.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    /** Unique index on app_user.email, see V1__init.sql. */
    private static final String EMAIL_UNIQUE_CONSTRAINT = "uq_user_email";

    /**
     * 400 with one entry per rejected field. Nested fields keep their path, so a
     * bad company name shows up as "company.name".
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidationFailure(MethodArgumentNotValidException exception) {
        Map<String, String> errors = new LinkedHashMap<>();
        exception.getBindingResult().getFieldErrors().forEach(error ->
                errors.putIfAbsent(error.getField(), error.getDefaultMessage()));
        exception.getBindingResult().getGlobalErrors().forEach(error ->
                errors.putIfAbsent(error.getObjectName(), error.getDefaultMessage()));

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, "Validation failed");
        problem.setTitle("Validation failed");
        problem.setProperty("errors", errors);
        return problem;
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
     * Safety net for the race the existsByEmail check cannot close: two
     * simultaneous registrations for the same address both pass the check and one
     * of them hits the unique index. Anything else is a real fault, so it is
     * rethrown and becomes a 500.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail handleDataIntegrityViolation(DataIntegrityViolationException exception) {
        if (!mentionsEmailConstraint(exception)) {
            throw exception;
        }
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT, "An account with this email already exists");
        problem.setTitle("Email already registered");
        return problem;
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
