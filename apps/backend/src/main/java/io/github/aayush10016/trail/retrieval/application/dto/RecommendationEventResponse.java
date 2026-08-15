package io.github.aayush10016.trail.retrieval.application.dto;

import io.github.aayush10016.trail.retrieval.domain.model.RecommendationEventOutcome;
import java.util.Objects;
import java.util.UUID;

/**
 * Response for a recommendation event write.
 */
public record RecommendationEventResponse(
        UUID recommendationId,
        RecommendationEventOutcome outcome,
        RecommendationEventStatus status) {

    /**
     * Creates an immutable response.
     */
    public RecommendationEventResponse {
        Objects.requireNonNull(recommendationId, "recommendationId");
        Objects.requireNonNull(outcome, "outcome");
        Objects.requireNonNull(status, "status");
    }

    /**
     * Stable event write status.
     */
    public enum RecommendationEventStatus {
        /**
         * Event was newly accepted.
         */
        ACCEPTED,

        /**
         * Event was already recorded and treated idempotently.
         */
        DUPLICATE
    }
}
