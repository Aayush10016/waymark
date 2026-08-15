package io.github.aayush10016.trail.retrieval.application.dto;

import io.github.aayush10016.trail.retrieval.domain.model.ProblemRecommendationStrength;
import io.github.aayush10016.trail.retrieval.domain.model.RecommendationExplanationMessage;
import io.github.aayush10016.trail.retrieval.domain.model.SimilarityEvidenceCode;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Bounded recommendation detail returned to extension clients.
 */
public record ProblemRecommendationDetailResponse(
        UUID recommendationId,
        UUID currentProblemSessionId,
        UUID recommendedProblemSessionId,
        ProblemRecommendationStrength strength,
        String title,
        String finalSolution,
        Instant solvedAt,
        Integer durationSeconds,
        String algorithmVersion,
        List<RecommendationEvidenceResponse> evidence,
        List<WhyThisMessageResponse> whyThis) {

    /**
     * Creates an immutable response.
     */
    public ProblemRecommendationDetailResponse {
        Objects.requireNonNull(recommendationId, "recommendationId");
        Objects.requireNonNull(currentProblemSessionId, "currentProblemSessionId");
        Objects.requireNonNull(recommendedProblemSessionId, "recommendedProblemSessionId");
        Objects.requireNonNull(strength, "strength");
        finalSolution = requireText(finalSolution, "finalSolution");
        algorithmVersion = requireText(algorithmVersion, "algorithmVersion");
        evidence = List.copyOf(Objects.requireNonNull(evidence, "evidence"));
        whyThis = List.copyOf(Objects.requireNonNull(whyThis, "whyThis"));
        if (evidence.isEmpty() || whyThis.isEmpty()) {
            throw new IllegalArgumentException("recommendation requires evidence");
        }
    }

    /**
     * Maps the application recommendation to the API response.
     */
    public static ProblemRecommendationDetailResponse from(TopProblemRecommendation recommendation) {
        var messages = recommendation.explanation().messages();
        return new ProblemRecommendationDetailResponse(
                recommendation.recommendationId(),
                recommendation.currentProblemSessionId(),
                recommendation.recommendedProblemSessionId(),
                recommendation.strength(),
                recommendation.title(),
                recommendation.finalSolution(),
                recommendation.solvedAt(),
                recommendation.durationSeconds(),
                recommendation.algorithmVersion(),
                messages.stream().map(RecommendationEvidenceResponse::from).toList(),
                messages.stream().map(WhyThisMessageResponse::from).toList());
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.strip();
    }

    /**
     * Structured evidence reference used by the client-side Why this UI.
     */
    public record RecommendationEvidenceResponse(
            SimilarityEvidenceCode evidenceCode, UUID problemSessionId, UUID artifactId) {

        static RecommendationEvidenceResponse from(RecommendationExplanationMessage message) {
            return new RecommendationEvidenceResponse(
                    message.evidenceCode(), message.problemSessionId(), message.artifactId().orElse(null));
        }
    }

    /**
     * Deterministic user-facing explanation message.
     */
    public record WhyThisMessageResponse(
            SimilarityEvidenceCode evidenceCode, UUID problemSessionId, UUID artifactId, String message) {

        static WhyThisMessageResponse from(RecommendationExplanationMessage message) {
            return new WhyThisMessageResponse(
                    message.evidenceCode(),
                    message.problemSessionId(),
                    message.artifactId().orElse(null),
                    message.message());
        }
    }
}
