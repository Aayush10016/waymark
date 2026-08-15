package io.github.aayush10016.trail.capture.application.dto;

/**
 * Stable safe rejection codes for capture ingestion validation.
 */
public enum CaptureIngestionRejectionCode {
    /**
     * Request contains a field not accepted by the backend capture contract.
     */
    UNKNOWN_FIELD,

    /**
     * Required URL field is absent or blank.
     */
    MISSING_URL,

    /**
     * Field type is incompatible with the backend capture contract.
     */
    INVALID_FIELD_TYPE,

    /**
     * Raw URL exceeds backend validation bounds.
     */
    URL_TOO_LARGE,

    /**
     * Raw title exceeds backend validation bounds.
     */
    TITLE_TOO_LARGE,

    /**
     * URL failed backend sanitation policy.
     */
    UNSAFE_URL
}
