package io.github.aayush10016.trail.identity.security;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * JWT access-token signing and verification properties.
 */
@ConfigurationProperties(prefix = "trail.identity.jwt")
public record JwtAccessTokenProperties(
        String keyId,
        String issuer,
        String audience,
        String publicKeyPem,
        String privateKeyPem,
        String publicKeyPath,
        String privateKeyPath,
        Duration accessTokenTimeToLive) {

    private static final Duration DEFAULT_ACCESS_TOKEN_TIME_TO_LIVE = Duration.ofMinutes(10);

    /**
     * Returns whether a public verification key is configured.
     */
    public boolean hasPublicKey() {
        return publicKeyPem != null && !publicKeyPem.isBlank();
    }

    /**
     * Returns whether a private signing key is configured.
     */
    public boolean hasPrivateKey() {
        return privateKeyPem != null && !privateKeyPem.isBlank();
    }

    /**
     * Returns the configured access-token lifetime or the v1.1 default.
     */
    public Duration accessTokenTimeToLiveOrDefault() {
        if (accessTokenTimeToLive == null) {
            return DEFAULT_ACCESS_TOKEN_TIME_TO_LIVE;
        }
        if (accessTokenTimeToLive.isZero() || accessTokenTimeToLive.isNegative()) {
            throw new IllegalArgumentException("trail.identity.jwt.access-token-time-to-live must be positive");
        }
        return accessTokenTimeToLive;
    }
}
