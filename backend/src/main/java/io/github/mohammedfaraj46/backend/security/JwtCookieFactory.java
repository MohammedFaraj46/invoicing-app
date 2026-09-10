package io.github.mohammedfaraj46.backend.security;

import io.github.mohammedfaraj46.backend.config.JwtProperties;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.Optional;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/**
 * Builds and reads the login cookie. httpOnly so JavaScript cannot touch the
 * token, SameSite=Lax so it is not sent along on cross-site POSTs.
 */
@Component
public class JwtCookieFactory {

    private final JwtProperties properties;

    public JwtCookieFactory(JwtProperties properties) {
        this.properties = properties;
    }

    public ResponseCookie issue(String token) {
        return baseCookie(token)
                .maxAge(properties.getExpiration())
                .build();
    }

    /** Same attributes with an empty value and Max-Age 0, which deletes it. */
    public ResponseCookie clear() {
        return baseCookie("")
                .maxAge(0)
                .build();
    }

    public Optional<String> readToken(HttpServletRequest request) {
        if (request.getCookies() == null) {
            return Optional.empty();
        }
        return Arrays.stream(request.getCookies())
                .filter(cookie -> properties.getCookieName().equals(cookie.getName()))
                .map(jakarta.servlet.http.Cookie::getValue)
                .filter(value -> value != null && !value.isBlank())
                .findFirst();
    }

    private ResponseCookie.ResponseCookieBuilder baseCookie(String value) {
        return ResponseCookie.from(properties.getCookieName(), value)
                .httpOnly(true)
                .secure(properties.isCookieSecure())
                .sameSite("Lax")
                .path("/");
    }
}
