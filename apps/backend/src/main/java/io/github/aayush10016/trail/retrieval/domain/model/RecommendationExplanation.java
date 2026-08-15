package io.github.aayush10016.trail.retrieval.domain.model;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Deterministic explanation for why one solved Problem Session was recommended.
 */
public record RecommendationExplanation(
        UUID recommendedProblemSessionId, String algorithmVersion, List<RecommendationExplanationMessage> messages) {

    /**
     * Creates a validated recommendation explanation.
     */
    public RecommendationExplanation {
        Objects.requireNonNull(recommendedProblemSessionId, "recommendedProblemSessionId");
        if (algorithmVersion == null || algorithmVersion.isBlank()) {
            throw new IllegalArgumentException("algorithmVersion must not be blank");
        }
        algorithmVersion = algorithmVersion.strip();
        messages = List.copyOf(Objects.requireNonNull(messages, "messages"));
        if (messages.isEmpty()) {
            throw new IllegalArgumentException("explanation requires at least one message");
        }
        if (messages.stream().anyMatch(message -> !message.problemSessionId().equals(recommendedProblemSessionId))) {
            throw new IllegalArgumentException("message references must belong to the recommended problem session");
        }
    }
}
