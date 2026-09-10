package io.github.mohammedfaraj46.backend.web.dto;

/**
 * Result of a successful registration. Deliberately carries no password or hash.
 */
public record RegisterResponse(
        Long userId,
        String email,
        Long companyId,
        String companyName
) {
}
