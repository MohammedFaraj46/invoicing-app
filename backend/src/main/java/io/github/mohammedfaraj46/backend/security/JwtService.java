package io.github.mohammedfaraj46.backend.security;

import io.github.mohammedfaraj46.backend.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;

/**
 * Issues and verifies the signed tokens that back the login cookie. The user id
 * is the subject; nothing else is put in the token, so a change to the account
 * takes effect on the next request rather than living on inside a claim.
 */
@Service
public class JwtService {

    /** HS256 needs a key of at least 256 bits. */
    private static final int MIN_SECRET_BYTES = 32;

    private final SecretKey signingKey;
    private final Duration expiration;

    public JwtService(JwtProperties properties) {
        byte[] secretBytes = properties.getSecret().getBytes(StandardCharsets.UTF_8);
        if (secretBytes.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "app.jwt.secret is too short: HS256 needs at least " + MIN_SECRET_BYTES
                            + " characters, got " + secretBytes.length
                            + ". Set the JWT_SECRET environment variable to a longer value.");
        }
        this.signingKey = Keys.hmacShaKeyFor(secretBytes);
        this.expiration = properties.getExpiration();
    }

    public String generateToken(Long userId) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(expiration)))
                .signWith(signingKey)
                .compact();
    }

    /**
     * Returns the user id when the token is well-formed, correctly signed and
     * unexpired; empty otherwise. A bad token is a normal event (expired,
     * tampered with, left over from an old secret), not an error worth throwing.
     */
    public Optional<Long> extractUserId(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return Optional.of(Long.valueOf(claims.getSubject()));
        } catch (JwtException | IllegalArgumentException exception) {
            return Optional.empty();
        }
    }
}
