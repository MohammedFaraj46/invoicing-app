package io.github.mohammedfaraj46.backend.service;

/**
 * Thrown when a customer id does not resolve for the signed-in company —
 * whether because it does not exist, or because it belongs to someone else.
 *
 * <p>Those two cases deliberately share one exception and one 404. A separate
 * 403 for "exists but not yours" would let a caller map out which ids are taken.
 */
public class CustomerNotFoundException extends RuntimeException {

    public CustomerNotFoundException(Long id) {
        super("No customer with id " + id);
    }
}
