package io.github.mohammedfaraj46.backend.security;

/**
 * Thrown when code asks who is signed in and nobody is. Mapped to HTTP 401 by
 * the web layer.
 *
 * <p>In practice the security filter chain already refuses those requests, so
 * this firing means a service was reached along a path that is not protected —
 * a configuration mistake, and one that should fail loudly rather than fall back
 * to some default user.
 */
public class UnauthenticatedException extends RuntimeException {

    public UnauthenticatedException() {
        super("No authenticated user");
    }
}
