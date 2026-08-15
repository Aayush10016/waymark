package io.github.aayush10016.trail.intelligence.domain.port;

import io.github.aayush10016.trail.intelligence.domain.model.StructuredAIRequest;
import io.github.aayush10016.trail.intelligence.domain.model.StructuredAIResult;

/**
 * Provider-neutral outbound port for structured AI generation.
 */
public interface AIProvider {

    /**
     * Generates a structured response for the supplied request.
     */
    StructuredAIResult generateStructured(StructuredAIRequest request);
}
