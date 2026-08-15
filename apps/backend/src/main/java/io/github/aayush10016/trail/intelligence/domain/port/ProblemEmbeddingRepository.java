package io.github.aayush10016.trail.intelligence.domain.port;

import io.github.aayush10016.trail.intelligence.domain.model.ProblemEmbedding;
import io.github.aayush10016.trail.intelligence.domain.model.EmbeddingModelGeneration;
import java.util.Optional;
import java.util.UUID;

/**
 * Outbound port for durable Problem Session embeddings.
 */
public interface ProblemEmbeddingRepository {

    /**
     * Persists one Problem Session embedding.
     */
    void save(ProblemEmbedding embedding);

    /**
     * Replaces any active embedding for the same session/generation with the supplied embedding.
     */
    void replaceActive(ProblemEmbedding embedding);

    /**
     * Finds the active embedding for a user-owned Problem Session and generation.
     */
    Optional<ProblemEmbedding> findActive(UUID userId, UUID problemSessionId, String generationKey, int generationVersion);

    /**
     * Finds the active embedding model generation for a generation key.
     */
    Optional<EmbeddingModelGeneration> findActiveGeneration(String generationKey);
}
