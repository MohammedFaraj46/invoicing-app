package io.github.mohammedfaraj46.backend.service;

/**
 * Thrown for any failed login. Deliberately carries no detail about which half
 * was wrong, so the response cannot be used to find out whether an email address
 * has an account. Mapped to HTTP 401 by the web layer.
 */
public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException() {
        super("Invalid email or password");
    }
}
