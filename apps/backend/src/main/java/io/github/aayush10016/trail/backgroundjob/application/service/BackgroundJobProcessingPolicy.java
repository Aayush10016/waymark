package io.github.aayush10016.trail.backgroundjob.application.service;

import java.time.Duration;
import java.util.Objects;

/**
 * Bounded processing limits for the in-process background job runner.
 */
public record BackgroundJobProcessingPolicy(
        int batchSize,
        int maxAttempts,
        Duration leaseDuration,
        Duration initialRetryBackoff,
        Duration maxRetryBackoff) {

    /**
     * Creates a validated processing policy.
     */
    public BackgroundJobProcessingPolicy {
        Objects.requireNonNull(leaseDuration, "leaseDuration");
        Objects.requireNonNull(initialRetryBackoff, "initialRetryBackoff");
        Objects.requireNonNull(maxRetryBackoff, "maxRetryBackoff");
        if (batchSize <= 0) {
            throw new IllegalArgumentException("batchSize must be positive");
        }
        if (maxAttempts <= 0) {
            throw new IllegalArgumentException("maxAttempts must be positive");
        }
        if (leaseDuration.isZero() || leaseDuration.isNegative()) {
            throw new IllegalArgumentException("leaseDuration must be positive");
        }
        if (initialRetryBackoff.isZero() || initialRetryBackoff.isNegative()) {
            throw new IllegalArgumentException("initialRetryBackoff must be positive");
        }
        if (maxRetryBackoff.compareTo(initialRetryBackoff) < 0) {
            throw new IllegalArgumentException("maxRetryBackoff must not be below initialRetryBackoff");
        }
    }

    /**
     * Calculates capped exponential retry delay for the already-recorded attempt count.
     */
    public Duration retryDelayForAttempt(int attempt) {
        if (attempt <= 0) {
            throw new IllegalArgumentException("attempt must be positive");
        }

        var multiplier = 1L;
        for (var index = 1; index < attempt; index += 1) {
            if (multiplier > Long.MAX_VALUE / 2L) {
                return maxRetryBackoff;
            }
            multiplier *= 2L;
        }

        Duration delay;
        try {
            delay = initialRetryBackoff.multipliedBy(multiplier);
        } catch (ArithmeticException exception) {
            return maxRetryBackoff;
        }
        return delay.compareTo(maxRetryBackoff) > 0 ? maxRetryBackoff : delay;
    }
}
