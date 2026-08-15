package io.github.aayush10016.trail.intelligence.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.aayush10016.trail.intelligence.adapter.out.ai.MockAIProvider;
import io.github.aayush10016.trail.intelligence.adapter.out.ai.OpenAICompatibleAIProvider;
import io.github.aayush10016.trail.intelligence.domain.port.AIProvider;
import java.net.http.HttpClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the selected AI provider adapter.
 */
@Configuration
@EnableConfigurationProperties(AIProviderProperties.class)
public class AIProviderConfiguration {

    /**
     * Creates the selected AI provider.
     */
    @Bean
    public AIProvider aiProvider(AIProviderProperties properties, ObjectMapper objectMapper) {
        return switch (properties.provider()) {
            case MOCK -> MockAIProvider.successful("{}");
            case OPENAI -> new OpenAICompatibleAIProvider(
                    HttpClient.newBuilder()
                            .connectTimeout(properties.openai().timeout())
                            .build(),
                    objectMapper,
                    properties.openai().responsesEndpoint(),
                    properties.openai().apiKey(),
                    new OpenAICompatibleAIProvider.OpenAICompatiblePricing(
                            properties.openai().inputCostPerMillionTokens(),
                            properties.openai().outputCostPerMillionTokens()));
        };
    }
}
