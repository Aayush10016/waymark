package io.github.aayush10016.trail.retrieval.domain.model;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Exact user-scoped vector candidate query for solved Problem Session embeddings.
 */
public record ProblemVectorCandidateQuery(
        UUID userId,
        String generationKey,
        List<Double> vector,
        int limit,
        Duration queryTimeout) {

    /**
     * Maximum number of vector candidates returned by one exact query.
     */
    public static final int MAXIMUM_LIMIT = 50;

    /**
     * Creates a validated query.
     */
    public ProblemVectorCandidateQuery {
        Objects.requireNonNull(userId, "userId");
        generationKey = requireText(generationKey, "generationKey", 128);
        vector = List.copyOf(Objects.requireNonNull(vector, "vector"));
        if (vector.isEmpty()) {
            throw new IllegalArgumentException("vector must not be empty");
        }
        vector.forEach(value -> {
            Objects.requireNonNull(value, "vector value");
            if (!Double.isFinite(value)) {
                throw new IllegalArgumentException("vector values must be finite");
            }
        });
        if (limit <= 0 || limit > MAXIMUM_LIMIT) {
            throw new IllegalArgumentException("limit must be between 1 and " + MAXIMUM_LIMIT);
        }
        Objects.requireNonNull(queryTimeout, "queryTimeout");
        if (queryTimeout.isZero() || queryTimeout.isNegative()) {
            throw new IllegalArgumentException("queryTimeout must be positive");
        }
    }

    private static String requireText(String value, String fieldName, int maximumLength) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        var normalized = value.strip();
        if (normalized.length() > maximumLength) {
            throw new IllegalArgumentException(fieldName + " must be at most " + maximumLength + " characters");
        }
        return normalized;
    }
}
