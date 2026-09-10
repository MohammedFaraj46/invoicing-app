package io.github.mohammedfaraj46.backend.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Payload for POST /api/auth/login.
 *
 * <p>No minimum length on the password here: this validates the shape of the
 * request, not the password policy. Rejecting a short password with a 400 would
 * also tell an attacker something about what is stored.
 */
public record LoginRequest(

        @NotBlank(message = "email is required")
        @Email(message = "email must be a valid email address")
        @Size(max = 255, message = "email must be at most 255 characters")
        String email,

        @NotBlank(message = "password is required")
        String password
) {
}
