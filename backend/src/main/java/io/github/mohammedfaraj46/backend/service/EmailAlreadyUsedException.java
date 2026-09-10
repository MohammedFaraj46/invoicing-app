package io.github.mohammedfaraj46.backend.service;

/**
 * Thrown when a registration uses an email address that is already taken.
 * Mapped to HTTP 409 by the web layer.
 */
public class EmailAlreadyUsedException extends RuntimeException {

    public EmailAlreadyUsedException(String email) {
        super("An account with email " + email + " already exists");
    }
}
