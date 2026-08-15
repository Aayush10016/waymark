package io.github.aayush10016.trail.intelligence.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.aayush10016.trail.intelligence.domain.model.ProblemSessionEnrichmentPromptCatalog;
import io.github.aayush10016.trail.intelligence.domain.service.PromptRegistry;
import org.junit.jupiter.api.Test;

class PromptRegistryConfigurationTest {

    @Test
    void wiresApprovedPromptRegistry() {
        var registry = new PromptRegistryConfiguration().promptRegistry();

        assertThat(registry.get(
                        ProblemSessionEnrichmentPromptCatalog.PROMPT_KEY,
                        ProblemSessionEnrichmentPromptCatalog.VERSION)
                .identity()
                .hash())
                .isEqualTo(ProblemSessionEnrichmentPromptCatalog.HASH);
    }
}
