package io.github.aayush10016.trail.identity.domain.port;

/**
 * Outbound port for creating high-entropy password reset tokens.
 */
public interface PasswordResetTokenGenerator {

    /**
     * Generates a raw password reset token. Callers must deliver it only through
     * trusted channels and must persist only its hash.
     *
     * @return raw password reset token
     */
    String generateToken();
}
