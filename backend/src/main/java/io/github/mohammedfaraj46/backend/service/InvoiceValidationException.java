package io.github.mohammedfaraj46.backend.service;

import java.util.Map;

/**
 * Thrown for a payload that passes bean validation but breaks a domain rule —
 * a VAT type that does not match its rate, for instance.
 *
 * <p>Carries the same field-to-message map that bean validation produces, so the
 * web layer can answer with one 400 shape and a form can put each message next
 * to the field it belongs to.
 */
public class InvoiceValidationException extends RuntimeException {

    private final Map<String, String> fieldErrors;

    public InvoiceValidationException(Map<String, String> fieldErrors) {
        super("Invoice validation failed: " + fieldErrors);
        this.fieldErrors = Map.copyOf(fieldErrors);
    }

    public Map<String, String> getFieldErrors() {
        return fieldErrors;
    }
}
