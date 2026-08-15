package io.github.aayush10016.trail.backgroundjob.application.service;

/**
 * Safe handler outcome categories for durable background job processing.
 */
public enum BackgroundJobHandlingStatus {
    /**
     * Handler completed successfully.
     */
    SUCCEEDED,

    /**
     * Handler failed in a way that may succeed later.
     */
    RETRYABLE_FAILURE,

    /**
     * Handler failed in a way that should not be retried.
     */
    TERMINAL_FAILURE
}
