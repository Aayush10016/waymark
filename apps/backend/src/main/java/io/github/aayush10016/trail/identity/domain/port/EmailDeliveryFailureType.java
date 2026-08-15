package io.github.aayush10016.trail.identity.domain.port;

/**
 * Email delivery failure retry classification.
 */
public enum EmailDeliveryFailureType {
    /**
     * Transient failure that can be retried.
     */
    RETRYABLE,

    /**
     * Permanent failure that should not be retried unchanged.
     */
    PERMANENT
}
