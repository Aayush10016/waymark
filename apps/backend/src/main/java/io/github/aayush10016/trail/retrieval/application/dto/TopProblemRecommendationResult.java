package io.github.aayush10016.trail.retrieval.application.dto;

import java.util.Objects;
import java.util.Optional;

/**
 * Recommendation lifecycle query result containing either one card or no match.
 */
public record TopProblemRecommendationResult(Optional<TopProblemRecommendation> recommendation) {

    /**
     * Creates a validated result.
     */
    public TopProblemRecommendationResult {
        recommendation = Objects.requireNonNull(recommendation, "recommendation");
    }

    /**
     * Creates a result with one recommendation card.
     */
    public static TopProblemRecommendationResult found(TopProblemRecommendation recommendation) {
        return new TopProblemRecommendationResult(Optional.of(recommendation));
    }

    /**
     * Creates a no-match result.
     */
    public static TopProblemRecommendationResult noMatch() {
        return new TopProblemRecommendationResult(Optional.empty());
    }
}
