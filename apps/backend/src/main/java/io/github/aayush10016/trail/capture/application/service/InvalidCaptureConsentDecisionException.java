package io.github.aayush10016.trail.capture.application.service;

/**
 * Raised when a capture consent decision request is malformed or unsupported.
 */
public class InvalidCaptureConsentDecisionException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * Creates the exception.
     */
    public InvalidCaptureConsentDecisionException(String message) {
        super(message);
    }
}
