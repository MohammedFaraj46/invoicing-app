package io.github.mohammedfaraj46.backend.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

/**
 * Answers an unauthenticated request with 401 and a problem+json body, in the
 * same shape the controllers and the access-denied handler use.
 *
 * <p>Replaces {@code HttpStatusEntryPoint}, which sets the status and writes
 * nothing at all — leaving the one status the SPA sees most often as the only
 * response in the API with no message to read.
 *
 * <p>The detail says nothing about why. Whether the cookie is missing, expired
 * or names an account that is gone is not something an unauthenticated caller
 * should learn, and the client's next move is the same in every case: sign in.
 */
public class ProblemDetailAuthenticationEntryPoint implements AuthenticationEntryPoint {

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException exception) throws IOException {

        ProblemDetailWriter.write(response, request,
                HttpStatus.UNAUTHORIZED, "Unauthorized", "Authentication required");
    }
}
