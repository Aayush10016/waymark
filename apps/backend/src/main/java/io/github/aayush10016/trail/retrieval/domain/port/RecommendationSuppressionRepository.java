package io.github.aayush10016.trail.retrieval.domain.port;

import java.util.UUID;

/**
 * Port for checking whether a recommendation pair is suppressed for a user.
 */
public interface RecommendationSuppressionRepository {

    /**
     * Returns true when the current/recommended pair must not be shown again.
     */
    boolean isSuppressed(UUID userId, UUID currentProblemSessionId, UUID recommendedProblemSessionId);
}
