package io.github.aayush10016.trail.intelligence.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.aayush10016.trail.intelligence.adapter.out.embedding.MockEmbeddingProvider;
import io.github.aayush10016.trail.intelligence.adapter.out.embedding.OpenAICompatibleEmbeddingProvider;
import java.math.BigDecimal;
import java.net.URI;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class EmbeddingProviderConfigurationTest {

    private final EmbeddingProviderConfiguration configuration = new EmbeddingProviderConfiguration();

    @Test
    void selectsMockProviderByDefault() {
        var provider = configuration.embeddingProvider(
                new EmbeddingProviderProperties(null, null),
                new ObjectMapper());

        assertThat(provider).isInstanceOf(MockEmbeddingProvider.class);
    }

    @Test
    void selectsOpenAICompatibleProviderWhenConfigured() {
        var provider = configuration.embeddingProvider(
                new EmbeddingProviderProperties(
                        EmbeddingProviderSelection.OPENAI,
                        new EmbeddingProviderProperties.OpenAI(
                                URI.create("http://127.0.0.1:9999/v1/embeddings"),
                                "test-api-key",
                                Duration.ofSeconds(5),
                                BigDecimal.ZERO)),
                new ObjectMapper());

        assertThat(provider).isInstanceOf(OpenAICompatibleEmbeddingProvider.class);
    }

    @Test
    void rejectsOpenAIProviderWithoutApiKeyWhenSelected() {
        assertThatThrownBy(() -> configuration.embeddingProvider(
                        new EmbeddingProviderProperties(
                                EmbeddingProviderSelection.OPENAI,
                                new EmbeddingProviderProperties.OpenAI(
                                        URI.create("http://127.0.0.1:9999/v1/embeddings"),
                                        "",
                                        Duration.ofSeconds(5),
                                        BigDecimal.ZERO)),
                        new ObjectMapper()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
