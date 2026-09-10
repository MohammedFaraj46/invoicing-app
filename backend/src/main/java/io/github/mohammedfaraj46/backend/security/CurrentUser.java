package io.github.mohammedfaraj46.backend.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * The one place where services ask who is signed in.
 *
 * <p>Every service that touches owned data gets the company id from here and
 * passes it into an ownership-filtered repository query — see the
 * {@code repository} package documentation. Keeping this in a single component
 * means there is exactly one answer to "whose data is this", and no service is
 * tempted to take an owner id from the request body or the URL, where a caller
 * could put someone else's.
 *
 * <p>Stateless on purpose: the authentication lives in a thread-local
 * {@code SecurityContextHolder} that the filter chain fills per request, so no
 * request scope is needed and the bean is safe to inject anywhere.
 */
@Component
public class CurrentUser {

    /**
     * @throws UnauthenticatedException if the request carries no signed-in user
     */
    public AppUserPrincipal principal() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        // an anonymous request has an Authentication too, but its principal is the
        // string "anonymousUser", so checking the type covers both cases
        if (authentication == null
                || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof AppUserPrincipal appUser)) {
            throw new UnauthenticatedException();
        }
        return appUser;
    }

    /** Id of the signed-in user. */
    public Long userId() {
        return principal().getId();
    }

    /**
     * Id of the company the signed-in user owns. This is the value that belongs
     * in every ownership-filtered query.
     *
     * @throws UnauthenticatedException if nobody is signed in
     * @throws IllegalStateException    if the account has no company, which
     *                                  registration should have made impossible
     */
    public Long companyId() {
        AppUserPrincipal appUser = principal();
        Long companyId = appUser.getCompanyId();
        if (companyId == null) {
            throw new IllegalStateException(
                    "User " + appUser.getId() + " has no company; registration should always create one");
        }
        return companyId;
    }
}
