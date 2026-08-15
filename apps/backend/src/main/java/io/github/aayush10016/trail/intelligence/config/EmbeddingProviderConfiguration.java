package io.github.aayush10016.trail.intelligence.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.aayush10016.trail.intelligence.adapter.out.embedding.MockEmbeddingProvider;
import io.github.aayush10016.trail.intelligence.adapter.out.embedding.OpenAICompatibleEmbeddingProvider;
import io.github.aayush10016.trail.intelligence.domain.port.EmbeddingProvider;
import java.net.http.HttpClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the selected embedding provider adapter.
 */
@Configuration
@EnableConfigurationProperties(EmbeddingProviderProperties.class)
public class EmbeddingProviderConfiguration {

    /**
     * Creates the selected embedding provider.
     */
    @Bean
    public EmbeddingProvider embeddingProvider(EmbeddingProviderProperties properties, ObjectMapper objectMapper) {
        return switch (properties.provider()) {
            case MOCK -> MockEmbeddingProvider.successful();
            case OPENAI -> new OpenAICompatibleEmbeddingProvider(
                    HttpClient.newBuilder()
                            .connectTimeout(properties.openai().timeout())
                            .build(),
                    objectMapper,
                    properties.openai().embeddingsEndpoint(),
                    properties.openai().apiKey(),
                    new OpenAICompatibleEmbeddingProvider.OpenAICompatibleEmbeddingPricing(
                            properties.openai().inputCostPerMillionTokens()));
        };
    }
}
