package io.github.mohammedfaraj46.backend.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.csrf.CsrfException;

/**
 * Answers a denied request with 403 and a problem+json body, in the same shape
 * the controllers use.
 *
 * <p>Spring's default handler calls {@code sendError}, which makes the container
 * re-dispatch to /error. That second pass runs through the security chain again
 * as an anonymous request, and the authentication entry point then turns the 403
 * into a 401. Writing the status straight onto the response avoids that.
 */
public class ProblemDetailAccessDeniedHandler implements AccessDeniedHandler {

    @Override
    public void handle(HttpServletRequest request,
                       HttpServletResponse response,
                       AccessDeniedException exception) throws IOException {

        String detail = (exception instanceof CsrfException)
                ? "Missing or invalid CSRF token"
                : "Access denied";

        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write("""
                {"type":"about:blank","title":"Forbidden","status":403,"detail":"%s","instance":"%s"}"""
                .formatted(detail, request.getRequestURI()));
    }
}
