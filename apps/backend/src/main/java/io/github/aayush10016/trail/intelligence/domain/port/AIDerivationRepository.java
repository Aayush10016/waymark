package io.github.aayush10016.trail.intelligence.domain.port;

import io.github.aayush10016.trail.intelligence.domain.model.AIDerivationRecord;
import io.github.aayush10016.trail.intelligence.domain.model.ProblemSessionEnrichmentProjection;

/**
 * Outbound port for durable AI provenance and atomic Problem Session projections.
 */
public interface AIDerivationRepository {

    /**
     * Persists one derivation attempt without modifying Problem Session projections.
     */
    void save(AIDerivationRecord derivation);

    /**
     * Persists successful provenance and projected fields in one transaction.
     */
    void saveSuccessfulEnrichment(AIDerivationRecord derivation, ProblemSessionEnrichmentProjection projection);
}
