package io.github.aayush10016.trail.retrieval.domain.model;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * User-owned current Problem Session data required to query prior solved recommendations.
 */
public record CurrentProblemRecommendationInput(
        UUID problemSessionId,
        String queryText,
        List<String> technologies,
        List<String> errorPhrases,
        String embeddingGenerationKey,
        List<Double> embeddingVector) {

    /**
     * Creates validated current recommendation input.
     */
    public CurrentProblemRecommendationInput {
        Objects.requireNonNull(problemSessionId, "problemSessionId");
        queryText = requireText(queryText, "queryText");
        technologies = List.copyOf(Objects.requireNonNull(technologies, "technologies"));
        errorPhrases = List.copyOf(Objects.requireNonNull(errorPhrases, "errorPhrases"));
        embeddingGenerationKey = requireText(embeddingGenerationKey, "embeddingGenerationKey");
        embeddingVector = List.copyOf(Objects.requireNonNull(embeddingVector, "embeddingVector"));
        if (embeddingVector.isEmpty()) {
            throw new IllegalArgumentException("embeddingVector must not be empty");
        }
        embeddingVector.forEach(value -> {
            Objects.requireNonNull(value, "embeddingVector value");
            if (!Double.isFinite(value)) {
                throw new IllegalArgumentException("embeddingVector values must be finite");
            }
        });
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.strip();
    }
}
