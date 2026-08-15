package io.github.aayush10016.trail.intelligence.domain.port;

import io.github.aayush10016.trail.intelligence.domain.model.ProblemSessionEnrichmentContext;
import java.util.Optional;
import java.util.UUID;

/**
 * Outbound port for reloading solved Problem Session context for enrichment.
 */
public interface ProblemSessionEnrichmentContextRepository {

    /**
     * Finds bounded solved-session enrichment context by composite ownership.
     */
    Optional<ProblemSessionEnrichmentContext> findSolvedContext(UUID userId, UUID problemSessionId);
}
