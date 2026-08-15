package io.github.aayush10016.trail.identity.domain.model;

import java.time.Instant;
import java.util.Objects;

/**
 * Issued bearer access token.
 *
 * @param token raw JWT access token returned once to the authenticated client
 * @param tokenType token type
 * @param expiresAt token expiry timestamp
 */
public record IssuedAccessToken(String token, String tokenType, Instant expiresAt) {

    private static final String BEARER_TOKEN_TYPE = "Bearer";

    /**
     * Creates a validated issued access token.
     */
    public IssuedAccessToken {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("token must not be blank");
        }
        if (tokenType == null || tokenType.isBlank()) {
            throw new IllegalArgumentException("tokenType must not be blank");
        }
        Objects.requireNonNull(expiresAt, "expiresAt");
    }

    /**
     * Creates a bearer token result.
     *
     * @param token raw JWT access token
     * @param expiresAt token expiry timestamp
     * @return bearer token result
     */
    public static IssuedAccessToken bearer(String token, Instant expiresAt) {
        return new IssuedAccessToken(token, BEARER_TOKEN_TYPE, expiresAt);
    }

    @Override
    public String toString() {
        return "IssuedAccessToken[tokenType=" + tokenType + ", expiresAt=" + expiresAt + "]";
    }
}
