package io.github.aayush10016.trail.intelligence.domain.model;

/**
 * Provider-neutral token usage metadata for an AI call.
 */
public record AIUsageMetadata(long inputTokens, long outputTokens, long totalTokens) {

    /**
     * Creates validated usage metadata.
     */
    public AIUsageMetadata {
        if (inputTokens < 0 || outputTokens < 0 || totalTokens < 0) {
            throw new IllegalArgumentException("token counts must not be negative");
        }
        if (totalTokens < inputTokens + outputTokens) {
            throw new IllegalArgumentException("totalTokens must include input and output tokens");
        }
    }

    /**
     * Empty usage metadata for local/mock calls.
     */
    public static AIUsageMetadata zero() {
        return new AIUsageMetadata(0, 0, 0);
    }
}
