package io.github.aayush10016.trail.retrieval.application.query;

import io.github.aayush10016.trail.retrieval.domain.model.ProblemRecommendationMode;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Query for selecting one recommendation candidate for the current Problem Session.
 */
public record FindTopProblemRecommendationQuery(
        UUID currentProblemSessionId,
        String queryText,
        List<String> technologies,
        List<String> errorPhrases,
        String embeddingGenerationKey,
        List<Double> embeddingVector,
        int candidateLimit,
        Duration vectorQueryTimeout,
        ProblemRecommendationMode mode) {

    /**
     * Creates a validated query.
     */
    public FindTopProblemRecommendationQuery {
        Objects.requireNonNull(currentProblemSessionId, "currentProblemSessionId");
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
        if (candidateLimit <= 0 || candidateLimit > 50) {
            throw new IllegalArgumentException("candidateLimit must be between 1 and 50");
        }
        Objects.requireNonNull(vectorQueryTimeout, "vectorQueryTimeout");
        if (vectorQueryTimeout.isZero() || vectorQueryTimeout.isNegative()) {
            throw new IllegalArgumentException("vectorQueryTimeout must be positive");
        }
        Objects.requireNonNull(mode, "mode");
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.strip();
    }
}
