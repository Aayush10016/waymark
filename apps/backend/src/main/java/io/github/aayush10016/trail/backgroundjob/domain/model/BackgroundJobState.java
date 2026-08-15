package io.github.aayush10016.trail.backgroundjob.domain.model;

/**
 * Durable background job lifecycle state.
 */
public enum BackgroundJobState {
    /**
     * Job is durably recorded and ready to be claimed once next_attempt_at is due.
     */
    READY,

    /**
     * Job is claimed by a worker lease and must not be processed by another owner until the lease expires.
     */
    LEASED,

    /**
     * Job completed successfully.
     */
    SUCCEEDED,

    /**
     * Job failed in a retryable way and can be claimed again once next_attempt_at is due.
     */
    FAILED_RETRYABLE,

    /**
     * Job failed permanently and will not be retried.
     */
    FAILED_TERMINAL
}
