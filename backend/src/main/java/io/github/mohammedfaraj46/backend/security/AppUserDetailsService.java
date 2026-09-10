package io.github.mohammedfaraj46.backend.security;

import io.github.mohammedfaraj46.backend.domain.AppUser;
import io.github.mohammedfaraj46.backend.domain.Company;
import io.github.mohammedfaraj46.backend.repository.AppUserRepository;
import io.github.mohammedfaraj46.backend.repository.CompanyRepository;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Loads accounts for Spring Security: by email when logging in, by id when a
 * request arrives with a token whose subject is that id.
 *
 * <p>Having this bean also stops Boot from auto-configuring an in-memory user,
 * which is what printed a generated password on every startup.
 */
@Service
public class AppUserDetailsService implements UserDetailsService {

    private final AppUserRepository appUserRepository;
    private final CompanyRepository companyRepository;

    public AppUserDetailsService(AppUserRepository appUserRepository,
                                 CompanyRepository companyRepository) {
        this.appUserRepository = appUserRepository;
        this.companyRepository = companyRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public AppUserPrincipal loadUserByUsername(String email) {
        AppUser user = appUserRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("No account for " + email));
        return toPrincipal(user);
    }

    @Transactional(readOnly = true)
    public AppUserPrincipal loadById(Long userId) {
        AppUser user = appUserRepository.findById(userId)
                .orElseThrow(() -> new UsernameNotFoundException("No account with id " + userId));
        return toPrincipal(user);
    }

    private AppUserPrincipal toPrincipal(AppUser user) {
        Long companyId = companyRepository.findByUserId(user.getId())
                .map(Company::getId)
                .orElse(null);
        return new AppUserPrincipal(user.getId(), user.getEmail(), user.getPasswordHash(), companyId);
    }
}
