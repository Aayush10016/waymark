package io.github.aayush10016.trail.intelligence.domain.port;

import io.github.aayush10016.trail.intelligence.domain.model.SolvedProblemEmbeddingContext;
import java.util.Optional;
import java.util.UUID;

/**
 * Outbound port for loading bounded solved-session data before embedding.
 */
public interface SolvedProblemEmbeddingContextRepository {

    /**
     * Finds one user-owned solved Problem Session context.
     */
    Optional<SolvedProblemEmbeddingContext> findSolvedContext(UUID userId, UUID problemSessionId);
}
