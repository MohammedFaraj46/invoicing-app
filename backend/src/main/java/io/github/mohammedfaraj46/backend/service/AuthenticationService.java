package io.github.mohammedfaraj46.backend.service;

import io.github.mohammedfaraj46.backend.security.AppUserDetailsService;
import io.github.mohammedfaraj46.backend.security.AppUserPrincipal;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Verifies a login against the stored BCrypt hash.
 */
@Service
public class AuthenticationService {

    private static final Logger log = LoggerFactory.getLogger(AuthenticationService.class);

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
            // WARN, and with the address: a run of these on one address is what a
            // brute-force attempt looks like from here. The response stays vague
            // on purpose, but the log may say which half failed.
            log.warn("Failed login for unknown email {}", normalisedEmail);
            throw new InvalidCredentialsException();
        }

        if (!passwordEncoder.matches(rawPassword, principal.getPassword())) {
            log.warn("Failed login for user {}: wrong password", principal.getId());
            throw new InvalidCredentialsException();
        }

        log.info("User {} signed in", principal.getId());
        return principal;
    }
}
