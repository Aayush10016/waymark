package io.github.aayush10016.trail.retrieval.domain.model;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Ranked solved Problem Session similarity match with deterministic evidence.
 */
public record HybridSimilarityMatch(
        UUID problemSessionId,
        double hybridScore,
        double vectorDistance,
        double lexicalScore,
        List<SimilarityEvidenceCode> evidenceCodes,
        String algorithmVersion) {

    /**
     * Creates a validated match.
     */
    public HybridSimilarityMatch {
        Objects.requireNonNull(problemSessionId, "problemSessionId");
        requireFiniteUnit(hybridScore, "hybridScore");
        requireFiniteUnit(vectorDistance, "vectorDistance");
        if (!Double.isFinite(lexicalScore) || lexicalScore <= 0) {
            throw new IllegalArgumentException("lexicalScore must be positive and finite");
        }
        evidenceCodes = List.copyOf(Objects.requireNonNull(evidenceCodes, "evidenceCodes"));
        if (!evidenceCodes.contains(SimilarityEvidenceCode.VECTOR_DISTANCE)) {
            throw new IllegalArgumentException("match requires vector evidence");
        }
        if (!hasSupportingEvidence(evidenceCodes)) {
            throw new IllegalArgumentException("match requires non-technology supporting evidence");
        }
        algorithmVersion = requireText(algorithmVersion, "algorithmVersion");
    }

    /**
     * Returns true when evidence contains non-technology support.
     */
    public static boolean hasSupportingEvidence(List<SimilarityEvidenceCode> evidenceCodes) {
        return evidenceCodes.contains(SimilarityEvidenceCode.LEXICAL_TEXT)
                || evidenceCodes.contains(SimilarityEvidenceCode.ERROR_PHRASE);
    }

    private static void requireFiniteUnit(double value, String fieldName) {
        if (!Double.isFinite(value) || value < 0 || value > 1) {
            throw new IllegalArgumentException(fieldName + " must be between 0 and 1");
        }
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.strip();
    }
}
