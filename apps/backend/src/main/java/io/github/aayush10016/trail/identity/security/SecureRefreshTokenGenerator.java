package io.github.aayush10016.trail.identity.security;

import io.github.aayush10016.trail.identity.domain.port.RefreshTokenGenerator;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Objects;

/**
 * SecureRandom-backed opaque refresh credential generator.
 */
public final class SecureRefreshTokenGenerator implements RefreshTokenGenerator {

    /**
     * Number of random bytes per refresh credential.
     */
    public static final int TOKEN_BYTES = 32;

    private final SecureRandom secureRandom;

    /**
     * Creates a refresh token generator.
     *
     * @param secureRandom secure random source
     */
    public SecureRefreshTokenGenerator(SecureRandom secureRandom) {
        this.secureRandom = Objects.requireNonNull(secureRandom, "secureRandom");
    }

    @Override
    public String generateToken() {
        var bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
