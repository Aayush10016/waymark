package io.github.aayush10016.trail.intelligence.adapter.out.ai;

/**
 * Deterministic mock AI provider behavior modes.
 */
public enum MockAIProviderBehavior {
    /**
     * Return a successful structured response.
     */
    SUCCESS,

    /**
     * Return a safe invalid-response failure.
     */
    INVALID_RESPONSE,

    /**
     * Return a safe retryable timeout failure.
     */
    TIMEOUT
}
