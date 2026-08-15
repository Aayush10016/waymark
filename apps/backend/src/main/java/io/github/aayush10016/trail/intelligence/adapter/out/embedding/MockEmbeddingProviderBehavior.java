package io.github.aayush10016.trail.intelligence.adapter.out.embedding;

/**
 * Deterministic mock embedding provider behavior modes.
 */
public enum MockEmbeddingProviderBehavior {
    /**
     * Return a deterministic vector with requested dimensions.
     */
    SUCCESS,

    /**
     * Return invalid-response failure.
     */
    INVALID_RESPONSE,

    /**
     * Return retryable timeout failure.
     */
    TIMEOUT
}
