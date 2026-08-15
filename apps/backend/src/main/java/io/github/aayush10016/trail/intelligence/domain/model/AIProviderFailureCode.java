package io.github.aayush10016.trail.intelligence.domain.model;

/**
 * Stable, content-free AI provider failure codes.
 */
public enum AIProviderFailureCode {
    /**
     * Provider did not respond within the configured timeout.
     */
    PROVIDER_TIMEOUT,

    /**
     * Provider response could not be mapped to the requested structured schema.
     */
    INVALID_PROVIDER_RESPONSE,

    /**
     * Provider request was rejected by local validation before any call.
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
