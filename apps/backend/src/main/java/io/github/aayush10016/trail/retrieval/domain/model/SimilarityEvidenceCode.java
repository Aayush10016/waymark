package io.github.aayush10016.trail.retrieval.domain.model;

/**
 * Evidence codes used by the hybrid similarity ranker.
 */
public enum SimilarityEvidenceCode {
    /**
     * Exact vector distance passed the semantic threshold.
     */
    VECTOR_DISTANCE,

    /**
     * Lexical text matched the current problem.
     */
    LEXICAL_TEXT,

    /**
     * Exact extracted error phrase matched.
     */
    ERROR_PHRASE,

    /**
     * Technology/framework overlap matched.
     */
    TECHNOLOGY
}
