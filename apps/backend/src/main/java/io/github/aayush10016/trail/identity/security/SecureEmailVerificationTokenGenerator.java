package io.github.aayush10016.trail.identity.security;

import io.github.aayush10016.trail.identity.domain.port.EmailVerificationTokenGenerator;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Objects;

/**
 * SecureRandom-backed email verification token generator.
 */
public final class SecureEmailVerificationTokenGenerator implements EmailVerificationTokenGenerator {

    /**
     * Number of random bytes per token.
     */
    public static final int TOKEN_BYTES = 32;

    private final SecureRandom secureRandom;

    /**
     * Creates a generator using the provided secure random source.
     *
     * @param secureRandom random source
     */
    public SecureEmailVerificationTokenGenerator(SecureRandom secureRandom) {
        this.secureRandom = Objects.requireNonNull(secureRandom, "secureRandom");
    }

    @Override
    public String generateToken() {
        var bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
