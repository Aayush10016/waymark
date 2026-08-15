package io.github.aayush10016.trail.intelligence.domain.model;

/**
 * Stable, content-free embedding provider failure codes.
 */
public enum EmbeddingProviderFailureCode {
    /**
     * Provider did not respond within the configured timeout.
     */
    PROVIDER_TIMEOUT,

    /**
     * Provider response was malformed or incompatible with requested dimensions.
     */
    INVALID_PROVIDER_RESPONSE,

    /**
     * Local request validation rejected the call.
     */
    INVALID_REQUEST,

    /**
     * Provider returned a transient upstream failure.
     */
    PROVIDER_UNAVAILABLE,

    /**
     * Provider returned an unrecoverable safe failure.
     */
    PROVIDER_REJECTED
}
