package io.github.aayush10016.trail.intelligence.application.service;

/**
 * Application outcome for a Problem Session enrichment job.
 */
public enum ProblemSessionEnrichmentJobStatus {
    /**
     * Enrichment completed and projections were written.
     */
    SUCCEEDED,

    /**
     * Job may be retried later.
     */
    RETRYABLE_FAILURE,

    /**
     * Job should stop retrying.
     */
    TERMINAL_FAILURE
}
