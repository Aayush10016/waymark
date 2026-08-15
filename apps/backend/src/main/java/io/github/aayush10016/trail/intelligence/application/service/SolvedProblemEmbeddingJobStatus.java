package io.github.aayush10016.trail.intelligence.application.service;

/**
 * Status for one solved-session embedding job attempt.
 */
public enum SolvedProblemEmbeddingJobStatus {
    /**
     * Job completed successfully or was already cached.
     */
    SUCCEEDED,

    /**
     * Job failed but may be retried safely.
     */
    RETRYABLE_FAILURE,

    /**
     * Job cannot be retried without changed input/configuration.
     */
    TERMINAL_FAILURE
}
