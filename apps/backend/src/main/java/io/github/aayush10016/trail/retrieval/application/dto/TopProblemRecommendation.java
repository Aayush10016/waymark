package io.github.aayush10016.trail.retrieval.application.dto;

import io.github.aayush10016.trail.retrieval.domain.model.ProblemRecommendationStrength;
import io.github.aayush10016.trail.retrieval.domain.model.RecommendationExplanation;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * The single current recommendation card selected for a candidate Problem Session.
 */
public record TopProblemRecommendation(
        UUID recommendationId,
        UUID currentProblemSessionId,
        UUID recommendedProblemSessionId,
        ProblemRecommendationStrength strength,
        String title,
        String finalSolution,
        Instant solvedAt,
        Integer durationSeconds,
        String algorithmVersion,
        RecommendationEmbeddingProvenance embeddingProvenance,
        RecommendationExplanation explanation) {

    /**
     * Creates a validated top recommendation.
     */
    public TopProblemRecommendation {
        Objects.requireNonNull(recommendationId, "recommendationId");
        Objects.requireNonNull(currentProblemSessionId, "currentProblemSessionId");
        Objects.requireNonNull(recommendedProblemSessionId, "recommendedProblemSessionId");
        if (currentProblemSessionId.equals(recommendedProblemSessionId)) {
            throw new IllegalArgumentException("current and recommended sessions must be distinct");
        }
        Objects.requireNonNull(strength, "strength");
        title = optionalText(title);
        finalSolution = requireText(finalSolution, "finalSolution");
        if (durationSeconds != null && durationSeconds < 0) {
            throw new IllegalArgumentException("durationSeconds must be non-negative");
        }
        algorithmVersion = requireText(algorithmVersion, "algorithmVersion");
        Objects.requireNonNull(embeddingProvenance, "embeddingProvenance");
        Objects.requireNonNull(explanation, "explanation");
    }

    private static String optionalText(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.strip();
    }
}
