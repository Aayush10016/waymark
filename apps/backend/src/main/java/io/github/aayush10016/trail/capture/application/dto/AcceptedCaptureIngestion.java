package io.github.aayush10016.trail.capture.application.dto;

import io.github.aayush10016.trail.capture.domain.model.AllowedCaptureSanitization;

/**
 * Accepted backend capture validation result.
 *
 * @param sanitizedEvent sanitized metadata safe for later ingestion stages
 */
public record AcceptedCaptureIngestion(AllowedCaptureSanitization sanitizedEvent)
        implements CaptureIngestionValidationResult {

    /**
     * Creates an accepted validation result.
     */
    public AcceptedCaptureIngestion {
        if (sanitizedEvent == null) {
            throw new IllegalArgumentException("sanitizedEvent must not be null");
        }
    }

    @Override
    public boolean accepted() {
        return true;
    }
}
