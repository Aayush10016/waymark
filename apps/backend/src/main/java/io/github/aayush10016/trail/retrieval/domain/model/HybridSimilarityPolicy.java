package io.github.aayush10016.trail.retrieval.domain.model;

import java.util.Objects;

/**
 * Conservative threshold policy for hybrid solved Problem Session similarity.
 */
public record HybridSimilarityPolicy(
        String algorithmVersion,
        double maximumVectorDistance,
        double minimumHybridScore,
        double vectorWeight,
        double lexicalWeight) {

    /**
     * Frozen v1 hybrid ranker algorithm version.
     */
    public static final String DEFAULT_ALGORITHM_VERSION = "hybrid-similarity-v1";

    /**
     * Creates the default conservative v1 policy.
     */
    public static HybridSimilarityPolicy conservativeV1() {
        return new HybridSimilarityPolicy(DEFAULT_ALGORITHM_VERSION, 0.30, 0.68, 0.70, 0.30);
    }

    /**
     * Creates the bounded lower-threshold recall policy used only after explicit recall.
     */
    public static HybridSimilarityPolicy possibleRecallV1() {
        return new HybridSimilarityPolicy(DEFAULT_ALGORITHM_VERSION, 0.38, 0.58, 0.70, 0.30);
    }

    /**
     * Creates a validated policy.
     */
    public HybridSimilarityPolicy {
        algorithmVersion = requireText(algorithmVersion, "algorithmVersion");
        requireUnitInterval(maximumVectorDistance, "maximumVectorDistance");
        requireUnitInterval(minimumHybridScore, "minimumHybridScore");
        requirePositiveWeight(vectorWeight, "vectorWeight");
        requirePositiveWeight(lexicalWeight, "lexicalWeight");
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.strip();
    }

    private static void requireUnitInterval(double value, String fieldName) {
        if (!Double.isFinite(value) || value < 0 || value > 1) {
            throw new IllegalArgumentException(fieldName + " must be between 0 and 1");
        }
    }

    private static void requirePositiveWeight(double value, String fieldName) {
        Objects.requireNonNull(value, fieldName);
        if (!Double.isFinite(value) || value <= 0) {
            throw new IllegalArgumentException(fieldName + " must be positive and finite");
        }
    }
}
