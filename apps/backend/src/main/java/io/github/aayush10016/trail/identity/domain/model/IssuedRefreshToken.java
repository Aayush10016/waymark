package io.github.aayush10016.trail.identity.domain.model;

import java.time.Instant;
import java.util.Objects;

/**
 * Issued opaque refresh credential.
 *
 * @param token raw refresh credential returned once to the authenticated client
 * @param tokenType token type
 * @param expiresAt refresh credential expiry
 */
public record IssuedRefreshToken(String token, String tokenType, Instant expiresAt) {

    private static final String BEARER_TOKEN_TYPE = "Bearer";

    /**
     * Creates a validated issued refresh credential.
     */
    public IssuedRefreshToken {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("token must not be blank");
        }
        if (tokenType == null || tokenType.isBlank()) {
            throw new IllegalArgumentException("tokenType must not be blank");
        }
        Objects.requireNonNull(expiresAt, "expiresAt");
    }

    /**
     * Creates a bearer refresh credential.
     *
     * @param token raw refresh credential
     * @param expiresAt expiry timestamp
     * @return issued refresh credential
     */
    public static IssuedRefreshToken bearer(String token, Instant expiresAt) {
        return new IssuedRefreshToken(token, BEARER_TOKEN_TYPE, expiresAt);
    }

    @Override
    public String toString() {
        return "IssuedRefreshToken[tokenType=" + tokenType + ", expiresAt=" + expiresAt + "]";
    }
}
