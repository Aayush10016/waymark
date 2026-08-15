package io.github.aayush10016.trail.intelligence.config;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Validated AI provider configuration.
 */
@ConfigurationProperties("trail.ai")
public record AIProviderProperties(AIProviderSelection provider, OpenAIProperties openai) {

    /**
     * Creates AI provider properties.
     */
    public AIProviderProperties {
        provider = provider == null ? AIProviderSelection.MOCK : provider;
        openai = openai == null ? OpenAIProperties.defaults() : openai;
        if (provider == AIProviderSelection.OPENAI && (openai.apiKey() == null || openai.apiKey().isBlank())) {
            throw new IllegalArgumentException("trail.ai.openai.api-key is required when trail.ai.provider=openai");
        }
    }

    /**
     * OpenAI-compatible adapter properties.
     */
    public record OpenAIProperties(
            URI responsesEndpoint,
            String apiKey,
            Duration timeout,
            BigDecimal inputCostPerMillionTokens,
            BigDecimal outputCostPerMillionTokens) {

        /**
         * Creates validated OpenAI properties.
         */
        public OpenAIProperties {
            responsesEndpoint = responsesEndpoint == null ? URI.create("https://api.openai.com/v1/responses") : responsesEndpoint;
            apiKey = apiKey == null ? "" : apiKey.strip();
            timeout = timeout == null ? Duration.ofSeconds(30) : timeout;
            inputCostPerMillionTokens = inputCostPerMillionTokens == null ? BigDecimal.ZERO : inputCostPerMillionTokens;
            outputCostPerMillionTokens = outputCostPerMillionTokens == null ? BigDecimal.ZERO : outputCostPerMillionTokens;
            if (timeout.isZero() || timeout.isNegative()) {
                throw new IllegalArgumentException("trail.ai.openai.timeout must be positive");
            }
            if (inputCostPerMillionTokens.signum() < 0 || outputCostPerMillionTokens.signum() < 0) {
                throw new IllegalArgumentException("trail.ai.openai token costs must not be negative");
            }
        }

        static OpenAIProperties defaults() {
            return new OpenAIProperties(
                    URI.create("https://api.openai.com/v1/responses"),
                    "",
                    Duration.ofSeconds(30),
                    BigDecimal.ZERO,
                    BigDecimal.ZERO);
        }
    }
}
