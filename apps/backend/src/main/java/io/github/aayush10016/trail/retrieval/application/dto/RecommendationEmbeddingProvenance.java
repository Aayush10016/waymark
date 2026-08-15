package io.github.aayush10016.trail.retrieval.application.dto;

/**
 * Bounded embedding provenance attached to a recommendation candidate.
 */
public record RecommendationEmbeddingProvenance(
        String generationKey, int generationVersion, String provider, String model, String inputFingerprint) {

    /**
     * Creates validated embedding provenance.
     */
    public RecommendationEmbeddingProvenance {
        generationKey = requireText(generationKey, "generationKey");
        if (generationVersion <= 0) {
            throw new IllegalArgumentException("generationVersion must be positive");
        }
        provider = requireText(provider, "provider");
        model = requireText(model, "model");
        inputFingerprint = requireSha256(inputFingerprint);
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.strip();
    }

    private static String requireSha256(String value) {
        var normalized = requireText(value, "inputFingerprint");
        if (!normalized.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("inputFingerprint must be lowercase SHA-256 hex");
        }
        return normalized;
    }
}
