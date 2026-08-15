package io.github.aayush10016.trail.intelligence.application.service;

import java.time.Duration;

/**
 * Provider-independent runtime policy for one Problem Session enrichment attempt.
 */
public record ProblemSessionEnrichmentPolicy(String model, Duration timeout) {

    /**
     * Creates validated enrichment policy.
     */
    public ProblemSessionEnrichmentPolicy {
        if (model == null || model.isBlank()) {
            throw new IllegalArgumentException("model must not be blank");
        }
        model = model.strip();
        if (timeout == null || timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("timeout must be positive");
        }
    }
}
