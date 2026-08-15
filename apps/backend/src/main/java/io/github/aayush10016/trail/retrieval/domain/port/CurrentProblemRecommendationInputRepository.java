package io.github.aayush10016.trail.retrieval.domain.port;

import io.github.aayush10016.trail.retrieval.domain.model.CurrentProblemRecommendationInput;
import java.util.Optional;
import java.util.UUID;

/**
 * Port for loading current Problem Session input needed by recommendation queries.
 */
public interface CurrentProblemRecommendationInputRepository {

    /**
     * Returns current candidate input when the session has active embedding data.
     */
    Optional<CurrentProblemRecommendationInput> findInput(UUID userId, UUID currentProblemSessionId);
}
