package io.github.aayush10016.trail.intelligence.domain.model;

/**
 * Durable status for one AI derivation attempt.
 */
public enum AIDerivationStatus {
    /**
     * Provider output was valid and projected.
     */
    SUCCEEDED,

    /**
     * Provider failure may be retried.
     */
    FAILED_RETRYABLE,

    /**
     * Provider failure should not be retried.
     */
    FAILED_TERMINAL,

    /**
     * Local redaction policy refused the provider call.
     */
    REFUSED,

    /**
     * Provider output did not satisfy Trail's structured contract.
     */
    INVALID_OUTPUT
}
