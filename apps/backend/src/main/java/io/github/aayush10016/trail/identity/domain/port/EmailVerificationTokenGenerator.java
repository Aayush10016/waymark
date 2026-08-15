package io.github.aayush10016.trail.identity.domain.port;

/**
 * Outbound port for creating high-entropy email verification tokens.
 */
public interface EmailVerificationTokenGenerator {

    /**
     * Generates a raw verification token. Callers must deliver it only through
     * trusted channels and must persist only its hash.
     *
     * @return raw verification token
     */
    String generateToken();
}
