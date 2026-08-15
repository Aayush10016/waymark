package io.github.aayush10016.trail.identity.domain.model;

import java.time.Instant;
import java.util.Objects;

/**
 * Normalized identity abuse-control decision input.
 *
 * @param action identity action being attempted
 * @param accountKey normalized account key or bounded action key
 * @param requestContext request source context
 * @param attemptedAt attempt timestamp
 */
public record IdentityRateLimitAttempt(
        IdentityRateLimitAction action,
        String accountKey,
        IdentityRequestContext requestContext,
        Instant attemptedAt) {

    /**
     * Creates a validated rate-limit attempt.
     */
    public IdentityRateLimitAttempt {
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(requestContext, "requestContext");
        Objects.requireNonNull(attemptedAt, "attemptedAt");
        if (accountKey == null || accountKey.isBlank()) {
            throw new IllegalArgumentException("accountKey must not be blank");
        }
        accountKey = accountKey.trim();
    }

    /**
     * Creates an attempt keyed by normalized email.
     *
     * @param action identity action
     * @param email normalized email value
     * @param requestContext request source context
     * @param attemptedAt attempt timestamp
     * @return rate-limit attempt
     */
    public static IdentityRateLimitAttempt forEmail(
            IdentityRateLimitAction action,
            EmailAddress email,
            IdentityRequestContext requestContext,
            Instant attemptedAt) {
        return new IdentityRateLimitAttempt(action, email.normalized(), requestContext, attemptedAt);
    }
}
