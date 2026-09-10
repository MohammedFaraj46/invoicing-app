package io.github.mohammedfaraj46.backend.support;

import java.util.List;
import java.util.Optional;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;

/**
 * Small helpers for driving the API over HTTP the way a browser would: reading
 * individual cookies out of a response and fetching a CSRF token to send back.
 */
public final class HttpTestSupport {

    public static final String CSRF_COOKIE_NAME = "XSRF-TOKEN";
    public static final String CSRF_HEADER_NAME = "X-XSRF-TOKEN";
    public static final String JWT_COOKIE_NAME = "jwt";

    private HttpTestSupport() {
    }

    /** A CSRF token together with the cookie a browser would send back with it. */
    public record Csrf(String token, String cookieHeader) {
    }

    /**
     * Bootstraps like a SPA does: one GET, then read the readable token cookie.
     */
    public static Csrf fetchCsrf(TestRestTemplate restTemplate) {
        ResponseEntity<String> response = restTemplate.getForEntity("/api/auth/csrf", String.class);
        String token = cookieValue(response, CSRF_COOKIE_NAME)
                .orElseThrow(() -> new AssertionError("no " + CSRF_COOKIE_NAME + " cookie on /api/auth/csrf"));
        return new Csrf(token, CSRF_COOKIE_NAME + "=" + token);
    }

    /** The raw Set-Cookie header for one cookie name, if the response set it. */
    public static Optional<String> setCookie(ResponseEntity<?> response, String cookieName) {
        List<String> headers = response.getHeaders().get(HttpHeaders.SET_COOKIE);
        if (headers == null) {
            return Optional.empty();
        }
        return headers.stream()
                .filter(header -> header.startsWith(cookieName + "="))
                .findFirst();
    }

    /** The value of one cookie set by the response. */
    public static Optional<String> cookieValue(ResponseEntity<?> response, String cookieName) {
        return setCookie(response, cookieName).map(HttpTestSupport::valueOf);
    }

    /** Turns a Set-Cookie header into the "name=value" pair a browser sends back. */
    public static String asRequestCookie(String setCookieHeader) {
        int end = setCookieHeader.indexOf(';');
        return end < 0 ? setCookieHeader : setCookieHeader.substring(0, end);
    }

    public static long maxAgeOf(String setCookieHeader) {
        for (String attribute : setCookieHeader.split(";")) {
            String trimmed = attribute.trim();
            if (trimmed.regionMatches(true, 0, "Max-Age=", 0, "Max-Age=".length())) {
                return Long.parseLong(trimmed.substring("Max-Age=".length()));
            }
        }
        throw new AssertionError("no Max-Age in " + setCookieHeader);
    }

    private static String valueOf(String setCookieHeader) {
        String pair = asRequestCookie(setCookieHeader);
        return pair.substring(pair.indexOf('=') + 1);
    }
}
