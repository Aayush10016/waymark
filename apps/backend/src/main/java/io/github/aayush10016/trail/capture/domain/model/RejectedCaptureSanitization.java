package io.github.aayush10016.trail.capture.domain.model;

/**
 * Rejected capture candidate with safe, stable policy metadata.
 *
 * @param policyVersion sanitation policy version
 * @param rejectionReason stable rejection reason
 */
public record RejectedCaptureSanitization(String policyVersion, CaptureRejectionReason rejectionReason)
        implements CaptureSanitizationResult {

    /**
     * Creates a rejected sanitation result.
     */
    public RejectedCaptureSanitization {
        if (policyVersion == null || policyVersion.isBlank()) {
            throw new IllegalArgumentException("policyVersion must not be blank");
        }
        if (rejectionReason == null) {
            throw new IllegalArgumentException("rejectionReason must not be null");
        }
    }
}
