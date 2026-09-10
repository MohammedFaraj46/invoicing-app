package io.github.mohammedfaraj46.backend.config;

import jakarta.validation.constraints.NotBlank;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Settings under {@code app.jwt}. The secret is bound from the JWT_SECRET
 * environment variable; a missing one leaves it blank and startup fails here
 * instead of the application running with an unusable key.
 */
@ConfigurationProperties("app.jwt")
@Validated
public class JwtProperties {

    @NotBlank(message = "is required: set the JWT_SECRET environment variable to at least 32 characters")
    private String secret;

    /** How long an issued token stays valid, and the cookie's Max-Age. */
    private Duration expiration = Duration.ofHours(4);

    private String cookieName = "jwt";

    /** Off for local HTTP development, on once the app is served over HTTPS. */
    private boolean cookieSecure = false;

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public Duration getExpiration() {
        return expiration;
    }

    public void setExpiration(Duration expiration) {
        this.expiration = expiration;
    }

    public String getCookieName() {
        return cookieName;
    }

    public void setCookieName(String cookieName) {
        this.cookieName = cookieName;
    }

    public boolean isCookieSecure() {
        return cookieSecure;
    }

    public void setCookieSecure(boolean cookieSecure) {
        this.cookieSecure = cookieSecure;
    }
}
