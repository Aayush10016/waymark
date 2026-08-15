package io.github.aayush10016.trail.intelligence.application.service;

import java.time.Duration;

/**
 * Provider-independent policy for solved Problem Session embedding jobs.
 */
public record SolvedProblemEmbeddingPolicy(String generationKey, Duration timeout) {

    /**
     * Creates validated embedding policy.
     */
    public SolvedProblemEmbeddingPolicy {
        if (generationKey == null || generationKey.isBlank()) {
            throw new IllegalArgumentException("generationKey must not be blank");
        }
        generationKey = generationKey.strip();
        if (timeout == null || timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("timeout must be positive");
        }
    }
}
