package io.github.aayush10016.trail.intelligence.domain.model;

/**
 * Stable deterministic reason codes for candidate scoring and boundaries.
 */
public enum CandidateReasonCode {
    /**
     * User/manual session boundary takes precedence over automatic evidence.
     */
    MANUAL_PRECEDENCE,

    /**
     * An inactivity boundary permits Trail to evaluate a candidate without interrupting active work.
     */
    INACTIVITY_BOUNDARY,

    /**
     * A topic-shift boundary permits Trail to evaluate the completed window.
     */
    TOPIC_SHIFT_BOUNDARY,

    /**
     * Bounded allowlisted search query evidence contributed to the score.
     */
    SEARCH_QUERY_SIGNAL,

    /**
     * Deterministic technical/error phrase evidence contributed to the score.
     */
    ERROR_SIGNAL,

    /**
     * Multiple sanitized domains contributed to the score.
     */
    MULTIPLE_DOMAIN_SIGNAL,

    /**
     * Consecutive domain transitions contributed to the score.
     */
    DOMAIN_TRANSITION_SIGNAL,

    /**
     * Repeated sanitized-domain visits contributed to the score.
     */
    REPEATED_DOMAIN_SIGNAL
}
