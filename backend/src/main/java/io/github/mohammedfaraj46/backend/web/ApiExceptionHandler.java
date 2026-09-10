package io.github.mohammedfaraj46.backend.web;

import io.github.mohammedfaraj46.backend.service.EmailAlreadyUsedException;
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
