package io.github.aayush10016.trail.identity.domain.port;

/**
 * Generates opaque refresh credentials.
 */
public interface RefreshTokenGenerator {

    /**
     * Generates a raw opaque refresh credential.
     *
     * @return raw refresh token
     */
    String generateToken();
}
