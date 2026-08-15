package io.github.aayush10016.trail.retrieval.domain.model;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * One deterministic user-facing explanation line mapped to a concrete evidence code and source ID.
 */
public record RecommendationExplanationMessage(
        SimilarityEvidenceCode evidenceCode, UUID problemSessionId, Optional<UUID> artifactId, String message) {

    /**
     * Creates a validated explanation message.
     */
    public RecommendationExplanationMessage {
        Objects.requireNonNull(evidenceCode, "evidenceCode");
        Objects.requireNonNull(problemSessionId, "problemSessionId");
        artifactId = Objects.requireNonNull(artifactId, "artifactId");
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("message must not be blank");
        }
        message = message.strip();
    }
}
