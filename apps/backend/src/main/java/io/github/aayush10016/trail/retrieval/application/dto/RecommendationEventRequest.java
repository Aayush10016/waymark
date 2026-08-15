package io.github.aayush10016.trail.retrieval.application.dto;

import io.github.aayush10016.trail.retrieval.domain.model.RecommendationEventOutcome;
import java.util.Objects;

/**
 * Request body for one client-submitted recommendation event.
 */
public record RecommendationEventRequest(RecommendationEventOutcome outcome) {

    /**
     * Creates a validated event request.
     */
    public RecommendationEventRequest {
        Objects.requireNonNull(outcome, "outcome");
        if (!outcome.clientSubmittable()) {
            throw new IllegalArgumentException("outcome is not client-submittable");
        }
    }
}
