package io.github.aayush10016.trail.capture.application.dto;

import io.github.aayush10016.trail.capture.domain.model.CurrentCaptureCandidate;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Backend capture-state summary consumed by extension clients.
 */
public record CaptureStateSummaryResponse(
        CurrentCandidateResponse currentCandidate,
        PromptEligibilityResponse promptEligibility,
        RecommendationSummaryResponse recommendation,
        CapturePolicyFreshnessResponse policyFreshness) {

    /**
     * Creates an immutable capture-state summary.
     */
    public CaptureStateSummaryResponse {
        Objects.requireNonNull(promptEligibility, "promptEligibility");
        Objects.requireNonNull(recommendation, "recommendation");
        Objects.requireNonNull(policyFreshness, "policyFreshness");
    }

    /**
     * Current candidate summary.
     */
    public record CurrentCandidateResponse(
            UUID problemSessionId, String status, String title, Instant startedAt, Instant updatedAt) {

        public static CurrentCandidateResponse from(CurrentCaptureCandidate candidate) {
            return new CurrentCandidateResponse(
                    candidate.problemSessionId(),
                    candidate.status(),
                    candidate.title(),
                    candidate.startedAt(),
                    candidate.updatedAt());
        }
    }

    /**
     * Whether backend prompts/actions are currently eligible.
     */
    public record PromptEligibilityResponse(String status, String reason) {

        public PromptEligibilityResponse {
            status = requireText(status, "status");
            reason = requireText(reason, "reason");
        }
    }

    /**
     * Lightweight recommendation summary. Full detail is read from the recommendation endpoint.
     */
    public record RecommendationSummaryResponse(UUID currentProblemSessionId, String status) {

        public RecommendationSummaryResponse {
            status = requireText(status, "status");
        }
    }

    /**
     * Capture consent policy freshness summary.
     */
    public record CapturePolicyFreshnessResponse(String requiredPolicyVersion, String observedPolicyVersion, boolean fresh) {

        public CapturePolicyFreshnessResponse {
            requiredPolicyVersion = requireText(requiredPolicyVersion, "requiredPolicyVersion");
            observedPolicyVersion = observedPolicyVersion == null || observedPolicyVersion.isBlank()
                    ? null
                    : observedPolicyVersion.strip();
        }
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.strip();
    }
}
