package io.github.mohammedfaraj46.backend.security;

import java.util.Collection;
import java.util.List;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * The authenticated user as Spring Security sees it. Carries the company id as
 * well, since that is the key every ownership check filters on.
 */
public class AppUserPrincipal implements UserDetails {

    private final Long id;
    private final String email;
    private final String passwordHash;
    private final Long companyId;

    public AppUserPrincipal(Long id, String email, String passwordHash, Long companyId) {
        this.id = id;
        this.email = email;
        this.passwordHash = passwordHash;
        this.companyId = companyId;
    }

    public Long getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public Long getCompanyId() {
        return companyId;
    }

    /** No roles yet; every account has the same rights over its own data. */
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of();
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return email;
    }
}
