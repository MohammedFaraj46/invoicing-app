package io.github.mohammedfaraj46.backend.service;

import io.github.mohammedfaraj46.backend.security.AppUserDetailsService;
import io.github.mohammedfaraj46.backend.security.AppUserPrincipal;
import java.util.Locale;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Verifies a login against the stored BCrypt hash.
 */
@Service
public class AuthenticationService {

    /**
     * A real BCrypt hash of a value nobody knows. When the email is unknown we
     * still run one comparison against it, so a failed login takes the same time
     * whether or not the account exists — otherwise the response time alone
     * reveals which addresses are registered.
     */
    private static final String DUMMY_HASH =
            "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";

    private final AppUserDetailsService userDetailsService;
    private final PasswordEncoder passwordEncoder;

    public AuthenticationService(AppUserDetailsService userDetailsService,
                                 PasswordEncoder passwordEncoder) {
        this.userDetailsService = userDetailsService;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * @return the authenticated user
     * @throws InvalidCredentialsException if the email is unknown or the password does not match
     */
    @Transactional(readOnly = true)
    public AppUserPrincipal authenticate(String email, String rawPassword) {
        String normalisedEmail = email.trim().toLowerCase(Locale.ROOT);

        AppUserPrincipal principal;
        try {
            principal = userDetailsService.loadUserByUsername(normalisedEmail);
        } catch (UsernameNotFoundException exception) {
            passwordEncoder.matches(rawPassword, DUMMY_HASH);
            throw new InvalidCredentialsException();
        }

        if (!passwordEncoder.matches(rawPassword, principal.getPassword())) {
            throw new InvalidCredentialsException();
        }
        return principal;
    }
}
