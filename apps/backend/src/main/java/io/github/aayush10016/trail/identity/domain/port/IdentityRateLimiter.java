package io.github.aayush10016.trail.identity.domain.port;

import io.github.aayush10016.trail.identity.domain.model.IdentityRateLimitAttempt;

/**
 * Port for identity abuse-control throttling decisions.
 */
public interface IdentityRateLimiter {

    /**
     * Returns whether the identity action may proceed.
     *
     * @param attempt normalized rate-limit decision input
     * @return true when processing may continue
     */
    boolean isAllowed(IdentityRateLimitAttempt attempt);
}
