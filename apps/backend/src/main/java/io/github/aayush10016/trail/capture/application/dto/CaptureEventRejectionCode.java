package io.github.aayush10016.trail.capture.application.dto;

/**
 * Stable safe capture event rejection codes returned to the extension.
 */
public enum CaptureEventRejectionCode {
    CONSENT_REQUIRED,
    INVALID_CLIENT_EVENT_ID,
    INVALID_CLIENT_INSTANCE_ID,
    INVALID_EVENT_TIME,
    INVALID_METADATA,
    INVALID_URL,
    UNSAFE_URL
}
