package io.github.aayush10016.trail.intelligence.config;

import io.github.aayush10016.trail.intelligence.domain.model.ProblemSessionEnrichmentPromptCatalog;
import io.github.aayush10016.trail.intelligence.domain.service.PromptRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires immutable prompt registry definitions.
 */
@Configuration
public class PromptRegistryConfiguration {

    /**
     * Creates the immutable prompt registry.
     */
    @Bean
    public PromptRegistry promptRegistry() {
        return new PromptRegistry(ProblemSessionEnrichmentPromptCatalog.definitions());
    }
}
