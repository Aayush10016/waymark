package io.github.aayush10016.trail.identity.config;

import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for Trail identity lifecycle use cases.
 */
@ConfigurationProperties(prefix = "trail.identity.lifecycle")
public record IdentityLifecycleProperties(
        Duration refreshTokenTimeToLive,
        Duration emailVerificationTokenTimeToLive,
        Duration passwordResetTokenTimeToLive,
        URI verificationDestination,
        URI passwordResetDestination) {

    private static final Duration DEFAULT_REFRESH_TOKEN_TIME_TO_LIVE = Duration.ofDays(30);
    private static final Duration DEFAULT_EMAIL_VERIFICATION_TOKEN_TIME_TO_LIVE = Duration.ofHours(24);
    private static final Duration DEFAULT_PASSWORD_RESET_TOKEN_TIME_TO_LIVE = Duration.ofHours(1);
    private static final URI DEFAULT_VERIFICATION_DESTINATION = URI.create("http://localhost:8080/verify-email");
    private static final URI DEFAULT_PASSWORD_RESET_DESTINATION = URI.create("http://localhost:8080/reset-password");

    /**
     * Returns the configured refresh-token lifetime or the v1.1 default.
     */
    public Duration refreshTokenTimeToLiveOrDefault() {
        return positiveOrDefault(refreshTokenTimeToLive, DEFAULT_REFRESH_TOKEN_TIME_TO_LIVE);
    }

    /**
     * Returns the configured email-verification token lifetime or the v1.1 default.
     */
    public Duration emailVerificationTokenTimeToLiveOrDefault() {
        return positiveOrDefault(emailVerificationTokenTimeToLive, DEFAULT_EMAIL_VERIFICATION_TOKEN_TIME_TO_LIVE);
    }

    /**
     * Returns the configured password-reset token lifetime or the v1.1 default.
     */
    public Duration passwordResetTokenTimeToLiveOrDefault() {
        return positiveOrDefault(passwordResetTokenTimeToLive, DEFAULT_PASSWORD_RESET_TOKEN_TIME_TO_LIVE);
    }

    /**
     * Returns the configured verification destination or the local default.
     */
    public URI verificationDestinationOrDefault() {
        return verificationDestination == null ? DEFAULT_VERIFICATION_DESTINATION : verificationDestination;
    }

    /**
     * Returns the configured password-reset destination or the local default.
     */
    public URI passwordResetDestinationOrDefault() {
        return passwordResetDestination == null ? DEFAULT_PASSWORD_RESET_DESTINATION : passwordResetDestination;
    }

    private static Duration positiveOrDefault(Duration value, Duration defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        if (value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException("identity lifecycle duration must be positive");
        }
        return value;
    }
}
