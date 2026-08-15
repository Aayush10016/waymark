package io.github.aayush10016.trail.retrieval.domain.model;

/**
 * Deterministic evidence codes for solved-session retrieval.
 */
public enum SearchEvidenceCode {
    /**
     * Query terms matched the solved Problem Session search document.
     */
    LEXICAL_TEXT,

    /**
     * Normalized error phrase overlap was present.
     */
    ERROR_PHRASE,

    /**
     * Technology overlap was present.
     */
    TECHNOLOGY
}
