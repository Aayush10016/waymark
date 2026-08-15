package io.github.aayush10016.trail.intelligence.config;

/**
 * Supported embedding provider adapters.
 */
public enum EmbeddingProviderSelection {
    /**
     * Deterministic local mock provider.
     */
    MOCK,

    /**
     * OpenAI-compatible embeddings HTTP API.
     */
    OPENAI
}
