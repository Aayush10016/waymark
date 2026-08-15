package io.github.aayush10016.trail.capture.application.dto;

import java.time.Instant;
import java.util.Objects;

/**
 * Server-side capture consent decision response.
 */
public record CaptureConsentDecisionResponse(String status, String policyVersion, Instant decidedAt) {

    /**
     * Creates a validated immutable consent decision response.
     */
    public CaptureConsentDecisionResponse {
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(policyVersion, "policyVersion");
        Objects.requireNonNull(decidedAt, "decidedAt");
    }
}
