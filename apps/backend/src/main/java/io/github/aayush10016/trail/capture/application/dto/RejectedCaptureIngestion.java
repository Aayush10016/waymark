package io.github.aayush10016.trail.capture.application.dto;

/**
 * Rejected backend capture validation result.
 *
 * @param code stable safe rejection code
 * @param policyVersion sanitation policy version when available
 * @param detail stable safe detail when available
 */
public record RejectedCaptureIngestion(CaptureIngestionRejectionCode code, String policyVersion, String detail)
        implements CaptureIngestionValidationResult {

    /**
     * Creates a rejected validation result.
     */
    public RejectedCaptureIngestion {
        if (code == null) {
            throw new IllegalArgumentException("code must not be null");
        }
    }

    @Override
    public boolean accepted() {
        return false;
    }
}
