package io.github.aayush10016.trail.intelligence.domain.port;

import io.github.aayush10016.trail.intelligence.domain.model.EmbeddingRequest;
import io.github.aayush10016.trail.intelligence.domain.model.EmbeddingResult;

/**
 * Provider-neutral outbound port for vector embeddings.
 */
public interface EmbeddingProvider {

    /**
     * Embeds one canonical text document.
     */
    EmbeddingResult embed(EmbeddingRequest request);
}
