package io.github.aayush10016.trail.capture.application.dto;

/**
 * Result of backend capture ingestion validation.
 */
public sealed interface CaptureIngestionValidationResult
        permits AcceptedCaptureIngestion, RejectedCaptureIngestion {

    /**
     * Returns whether the input is acceptable for later ingestion.
     */
    boolean accepted();
}
