package io.github.aayush10016.trail.retrieval.domain.model;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Structured evidence reference used to explain a recommendation deterministically.
 */
public record RecommendationEvidenceReference(
        UUID problemSessionId, Optional<UUID> artifactId, SimilarityEvidenceCode evidenceCode) {

    /**
     * Creates a validated evidence reference.
     */
    public RecommendationEvidenceReference {
        Objects.requireNonNull(problemSessionId, "problemSessionId");
        artifactId = Objects.requireNonNull(artifactId, "artifactId");
        Objects.requireNonNull(evidenceCode, "evidenceCode");
    }

    /**
     * Creates a session-level evidence reference.
     */
    public static RecommendationEvidenceReference session(UUID problemSessionId, SimilarityEvidenceCode evidenceCode) {
        return new RecommendationEvidenceReference(problemSessionId, Optional.empty(), evidenceCode);
    }

    /**
     * Creates an artifact-level evidence reference.
     */
    public static RecommendationEvidenceReference artifact(
            UUID problemSessionId, UUID artifactId, SimilarityEvidenceCode evidenceCode) {
        return new RecommendationEvidenceReference(problemSessionId, Optional.of(artifactId), evidenceCode);
    }
}
