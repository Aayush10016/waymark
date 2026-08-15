package io.github.aayush10016.trail.backgroundjob.domain.model;

/**
 * Known background job work types.
 */
public enum BackgroundJobType {
    /**
     * Future asynchronous enrichment for a solved Problem Session.
     */
    PROBLEM_SESSION_ENRICHMENT,

    /**
     * Asynchronous embedding generation for a solved Problem Session.
     */
    SOLVED_PROBLEM_SESSION_EMBEDDING
}
