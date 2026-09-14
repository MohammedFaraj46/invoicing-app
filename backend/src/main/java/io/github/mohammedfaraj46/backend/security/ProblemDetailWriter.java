package io.github.mohammedfaraj46.backend.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

/**
 * Writes a problem+json body straight onto the response, for the two failures
 * that happen in the filter chain and so never reach the controller advice.
 *
 * <p>Written by hand rather than through the message converters: at this point
 * in the chain there is no handler and no content negotiation to hang them on.
 * Only the request path varies, and it is escaped, so the result is always valid
 * JSON.
 */
final class ProblemDetailWriter {

    private ProblemDetailWriter() {
    }

    static void write(HttpServletResponse response, HttpServletRequest request,
                      HttpStatus status, String title, String detail) throws IOException {

        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write("""
                {"type":"about:blank","title":"%s","status":%d,"detail":"%s","instance":"%s"}"""
                .formatted(title, status.value(), detail, escape(request.getRequestURI())));
    }

    /**
     * The request path is the one part that comes from the caller. It arrives
     * still percent-encoded, so a quote cannot normally reach here, but escaping
     * it costs nothing and means a malformed path cannot produce malformed JSON.
     */
    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
