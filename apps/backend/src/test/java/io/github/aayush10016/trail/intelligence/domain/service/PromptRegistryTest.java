package io.github.aayush10016.trail.intelligence.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.aayush10016.trail.intelligence.domain.model.ProblemSessionEnrichmentPromptCatalog;
import io.github.aayush10016.trail.intelligence.domain.model.PromptDefinition;
import io.github.aayush10016.trail.intelligence.domain.model.PromptIdentity;
import java.util.List;
import org.junit.jupiter.api.Test;

class PromptRegistryTest {

    @Test
    void looksUpPromptByKeyVersionAndIdentity() {
        var registry = new PromptRegistry(ProblemSessionEnrichmentPromptCatalog.definitions());

        var prompt = registry.get(
                ProblemSessionEnrichmentPromptCatalog.PROMPT_KEY,
                ProblemSessionEnrichmentPromptCatalog.VERSION);

        assertThat(prompt.identity().hash()).isEqualTo(ProblemSessionEnrichmentPromptCatalog.HASH);
        assertThat(prompt.outputSchemaJson()).contains("\"summary\"");
        assertThat(registry.get(prompt.identity())).isEqualTo(prompt);
        assertThat(registry.latest(ProblemSessionEnrichmentPromptCatalog.PROMPT_KEY)).isEqualTo(prompt);
    }

    @Test
    void rejectsDuplicateKeyVersion() {
        var prompt = ProblemSessionEnrichmentPromptCatalog.definitions().get(0);

        assertThatThrownBy(() -> new PromptRegistry(List.of(prompt, prompt)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("duplicate");
    }

    @Test
    void rejectsChangedPromptContentWithoutHashUpdate() {
        var prompt = ProblemSessionEnrichmentPromptCatalog.definitions().get(0);

        assertThatThrownBy(() -> new PromptDefinition(
                        prompt.identity(),
                        prompt.body() + " Changed.",
                        prompt.outputSchemaJson()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("hash");
    }

    @Test
    void rejectsIdentityHashMismatchOnLookup() {
        var registry = new PromptRegistry(ProblemSessionEnrichmentPromptCatalog.definitions());
        var mismatchedIdentity = new PromptIdentity(
                ProblemSessionEnrichmentPromptCatalog.PROMPT_KEY,
                ProblemSessionEnrichmentPromptCatalog.VERSION,
                "0000000000000000000000000000000000000000000000000000000000000000");

        assertThatThrownBy(() -> registry.get(mismatchedIdentity))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("hash");
    }

    @Test
    void goldenProblemSessionEnrichmentHashRemainsStable() {
        var prompt = ProblemSessionEnrichmentPromptCatalog.definitions().get(0);

        assertThat(PromptDefinition.contentHash(prompt.body(), prompt.outputSchemaJson()))
                .isEqualTo("7cd15398144e53a89063a7ab5807c641c1580fd3f4adcd5600140b003904896b");
    }
}
