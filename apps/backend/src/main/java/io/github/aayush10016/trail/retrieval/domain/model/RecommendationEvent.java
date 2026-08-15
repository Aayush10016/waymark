package io.github.aayush10016.trail.retrieval.domain.model;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Framework-free recommendation lifecycle event to persist.
 */
public record RecommendationEvent(
        UUID id,
        UUID userId,
        UUID recommendationId,
        UUID currentProblemSessionId,
        UUID recommendedProblemSessionId,
        RecommendationEventOutcome outcome,
        List<UUID> retrievedProblemSessionIds,
        List<RecommendationEvidenceReference> evidence,
        RecommendationEventProvenance embeddingProvenance,
        String algorithmVersion,
        Instant createdAt) {

    /**
     * Creates a validated immutable recommendation event.
     */
    public RecommendationEvent {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(recommendationId, "recommendationId");
        Objects.requireNonNull(currentProblemSessionId, "currentProblemSessionId");
        Objects.requireNonNull(recommendedProblemSessionId, "recommendedProblemSessionId");
        if (currentProblemSessionId.equals(recommendedProblemSessionId)) {
            throw new IllegalArgumentException("current and recommended sessions must be distinct");
        }
        Objects.requireNonNull(outcome, "outcome");
        retrievedProblemSessionIds = List.copyOf(Objects.requireNonNull(retrievedProblemSessionIds, "retrievedProblemSessionIds"));
        evidence = List.copyOf(Objects.requireNonNull(evidence, "evidence"));
        if (retrievedProblemSessionIds.isEmpty() || retrievedProblemSessionIds.size() > 50) {
            throw new IllegalArgumentException("retrievedProblemSessionIds must contain between 1 and 50 items");
        }
        if (evidence.isEmpty() || evidence.size() > 50) {
            throw new IllegalArgumentException("evidence must contain between 1 and 50 items");
        }
        Objects.requireNonNull(embeddingProvenance, "embeddingProvenance");
        if (algorithmVersion == null || algorithmVersion.isBlank()) {
            throw new IllegalArgumentException("algorithmVersion must not be blank");
        }
        algorithmVersion = algorithmVersion.strip();
        Objects.requireNonNull(createdAt, "createdAt");
    }
}
