package io.github.aayush10016.trail.intelligence.domain.model;

/**
 * Stable deterministic reason codes for debugging-detection evidence.
 */
public enum DetectionReasonCode {
    /**
     * No sanitized capture events were available.
     */
    NO_EVENTS,

    /**
     * A bounded chronological event window exists.
     */
    TIME_WINDOW_PRESENT,

    /**
     * At least one allowlisted sanitized search query exists.
     */
    SEARCH_QUERY_PRESENT,

    /**
     * At least one deterministic technical error phrase was found.
     */
    ERROR_PHRASE_PRESENT,

    /**
     * More than one domain appears in the bounded window.
     */
    MULTIPLE_DOMAINS_PRESENT,

    /**
     * Consecutive events moved between domains.
     */
    DOMAIN_TRANSITION_PRESENT,

    /**
     * At least one domain appears multiple times.
     */
    REPEATED_DOMAIN_PRESENT
}
