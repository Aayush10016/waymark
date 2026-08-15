package io.github.aayush10016.trail.intelligence.domain.model;

/**
 * AI redaction decision status.
 */
public enum AIRedactionStatus {
    /**
     * Safe bounded input can be sent to a provider.
     */
    ALLOWED,

    /**
     * Input must not be sent to a provider.
     */
    REFUSED
}
