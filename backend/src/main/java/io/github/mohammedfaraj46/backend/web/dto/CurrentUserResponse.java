package io.github.mohammedfaraj46.backend.web.dto;

import io.github.mohammedfaraj46.backend.security.AppUserPrincipal;

/**
 * The signed-in user, as returned by login and GET /api/auth/me.
 */
public record CurrentUserResponse(
        Long id,
        String email,
        Long companyId
) {

    public static CurrentUserResponse from(AppUserPrincipal principal) {
        return new CurrentUserResponse(principal.getId(), principal.getEmail(), principal.getCompanyId());
    }
}
