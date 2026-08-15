package io.github.aayush10016.trail.intelligence.domain.model;

import java.util.List;
import java.util.Objects;

/**
 * Immutable result of deterministic candidate scoring and suppression.
 *
 * @param status candidate or suppressed outcome
 * @param score bounded deterministic score
 * @param threshold score required for automatic candidate creation
 * @param algorithmVersion stable scoring policy version
 * @param reasonCodes stable positive/boundary reason codes
 * @param suppressionReason stable suppression reason when suppressed
 */
public record CandidateDetectionDecision(
        CandidateDecisionStatus status,
        int score,
        int threshold,
        String algorithmVersion,
        List<CandidateReasonCode> reasonCodes,
        CandidateSuppressionReasonCode suppressionReason) {

    /**
     * Creates an immutable decision.
     */
    public CandidateDetectionDecision {
        Objects.requireNonNull(status, "status");
        algorithmVersion = requiredText("algorithmVersion", algorithmVersion);
        reasonCodes = List.copyOf(reasonCodes);
        if (score < 0 || threshold < 0) {
            throw new IllegalArgumentException("score and threshold must be non-negative");
        }
        if (status == CandidateDecisionStatus.CANDIDATE && suppressionReason != null) {
            throw new IllegalArgumentException("candidate decisions must not include a suppression reason");
        }
        if (status == CandidateDecisionStatus.SUPPRESSED && suppressionReason == null) {
            throw new IllegalArgumentException("suppressed decisions require a suppression reason");
        }
    }

    /**
     * Returns true when the decision creates a candidate boundary for later workflows.
     */
    public boolean isCandidate() {
        return status == CandidateDecisionStatus.CANDIDATE;
    }

    private static String requiredText(String fieldName, String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.strip();
    }
}
