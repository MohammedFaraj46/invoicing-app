package io.github.mohammedfaraj46.backend.service;

import io.github.mohammedfaraj46.backend.domain.AppUser;
import io.github.mohammedfaraj46.backend.domain.Company;
import io.github.mohammedfaraj46.backend.repository.AppUserRepository;
import io.github.mohammedfaraj46.backend.repository.CompanyRepository;
import io.github.mohammedfaraj46.backend.web.dto.CompanyRegistration;
import io.github.mohammedfaraj46.backend.web.dto.RegisterRequest;
import io.github.mohammedfaraj46.backend.web.dto.RegisterResponse;
import java.util.Locale;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates a login account together with its company profile.
 *
 * <p>A user without a company would be unusable — every other entity hangs off
 * the company for ownership — so both are written in one transaction.
 */
@Service
public class RegistrationService {

    /** Fallback payment term for a fresh company, in days. */
    private static final int DEFAULT_PAYMENT_TERM_DAYS = 30;

    private final AppUserRepository appUserRepository;
    private final CompanyRepository companyRepository;
    private final PasswordEncoder passwordEncoder;

    public RegistrationService(AppUserRepository appUserRepository,
                               CompanyRepository companyRepository,
                               PasswordEncoder passwordEncoder) {
        this.appUserRepository = appUserRepository;
        this.companyRepository = companyRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public RegisterResponse register(RegisterRequest request) {
        String email = normaliseEmail(request.email());

        if (appUserRepository.existsByEmail(email)) {
            throw new EmailAlreadyUsedException(email);
        }

        AppUser user = new AppUser(email, passwordEncoder.encode(request.password()));
        appUserRepository.save(user);

        CompanyRegistration details = request.company();
        Company company = new Company(
                user,
                details.name(),
                details.kvkNumber(),
                details.vatNumber(),
                details.addressLine(),
                details.postalCode(),
                details.city(),
                details.country()
        );
        company.setDefaultPaymentTermDays(DEFAULT_PAYMENT_TERM_DAYS);
        companyRepository.save(company);

        // keep the in-memory graph consistent with what was just persisted
        user.setCompany(company);

        return new RegisterResponse(user.getId(), user.getEmail(),
                company.getId(), company.getName());
    }

    /**
     * Lower-cases and trims, so Foo@Example.nl and foo@example.nl cannot both be
     * registered. The uniqueness check and the stored value use the same form.
     */
    private static String normaliseEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
