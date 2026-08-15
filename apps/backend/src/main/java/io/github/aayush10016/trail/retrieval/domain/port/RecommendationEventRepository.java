package io.github.aayush10016.trail.retrieval.domain.port;

import io.github.aayush10016.trail.retrieval.domain.model.RecommendationEvent;
import java.util.Optional;
import java.util.UUID;

/**
 * Port for recording and reading recommendation lifecycle events.
 */
public interface RecommendationEventRepository {

    /**
     * Inserts the event if the same user/recommendation/outcome has not already been recorded.
     */
    RecommendationEventWriteResult insertIfAbsent(RecommendationEvent event);

    /**
     * Finds the server-derived shown event used as canonical ownership/provenance source.
     */
    Optional<RecommendationEvent> findShown(UUID userId, UUID recommendationId);

    /**
     * Recommendation event write result.
     */
    enum RecommendationEventWriteResult {
        /**
         * Event was newly inserted.
         */
        INSERTED,

        /**
         * Event was already recorded and the request is idempotent.
         */
        DUPLICATE
    }
}
