package io.github.aayush10016.trail.intelligence.domain.model;

/**
 * Stable deterministic reason codes for suppressed candidate decisions.
 */
public enum CandidateSuppressionReasonCode {
    /**
     * No bounded events were available.
     */
    EMPTY_FEATURES,

    /**
     * Automatic detection cannot progress while active navigation has not reached a safe boundary.
     */
    ACTIVE_NAVIGATION_BOUNDARY_NOT_REACHED,

    /**
     * The bounded window is too long to be treated as one coherent candidate.
     */
    TOPIC_GAP_TOO_LARGE,

    /**
     * The same automatic signal is still within a dismissal suppression window.
     */
    DISMISSED_SUPPRESSION_ACTIVE,

    /**
     * Evidence did not meet the conservative candidate threshold.
     */
    INSUFFICIENT_EVIDENCE
}
