package io.github.mohammedfaraj46.backend.web.dto;

import org.springframework.security.web.csrf.CsrfToken;

/**
 * The CSRF token, plus the header name it has to come back in. Lets a SPA
 * bootstrap without hard-coding "X-XSRF-TOKEN" on the client side.
 */
public record CsrfTokenResponse(
        String token,
        String headerName,
        String parameterName
) {

    public static CsrfTokenResponse from(CsrfToken csrfToken) {
        return new CsrfTokenResponse(
                csrfToken.getToken(),
                csrfToken.getHeaderName(),
                csrfToken.getParameterName());
    }
}
