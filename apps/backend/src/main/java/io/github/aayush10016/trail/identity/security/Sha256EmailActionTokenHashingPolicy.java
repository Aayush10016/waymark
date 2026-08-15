package io.github.aayush10016.trail.identity.security;

import io.github.aayush10016.trail.identity.domain.model.EmailActionTokenHash;
import io.github.aayush10016.trail.identity.domain.port.EmailActionTokenHashingPolicy;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * SHA-256 hashing policy for email action tokens.
 */
public final class Sha256EmailActionTokenHashingPolicy implements EmailActionTokenHashingPolicy {

    @Override
    public EmailActionTokenHash hash(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new IllegalArgumentException("rawToken must not be blank");
        }
        return new EmailActionTokenHash(messageDigest().digest(rawToken.getBytes(StandardCharsets.UTF_8)));
    }

    private static MessageDigest messageDigest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }
}
