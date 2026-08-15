package io.github.aayush10016.trail.intelligence.config;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for selected embedding provider.
 */
@ConfigurationProperties(prefix = "trail.embedding")
public record EmbeddingProviderProperties(EmbeddingProviderSelection provider, OpenAI openai) {

    /**
     * Creates validated embedding provider configuration.
     */
    public EmbeddingProviderProperties {
        provider = provider == null ? EmbeddingProviderSelection.MOCK : provider;
        openai = openai == null ? OpenAI.defaults() : openai;
    }

    /**
     * OpenAI-compatible embedding API configuration.
     */
    public record OpenAI(URI embeddingsEndpoint, String apiKey, Duration timeout, BigDecimal inputCostPerMillionTokens) {

        private static final URI DEFAULT_ENDPOINT = URI.create("https://api.openai.com/v1/embeddings");
        private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);

        /**
         * Creates validated OpenAI-compatible embedding configuration.
         */
        public OpenAI {
            embeddingsEndpoint = embeddingsEndpoint == null ? DEFAULT_ENDPOINT : embeddingsEndpoint;
            apiKey = apiKey == null ? "" : apiKey.strip();
            timeout = timeout == null ? DEFAULT_TIMEOUT : timeout;
            inputCostPerMillionTokens =
                    inputCostPerMillionTokens == null ? BigDecimal.ZERO : inputCostPerMillionTokens;
            if (timeout.isZero() || timeout.isNegative()) {
                throw new IllegalArgumentException("embedding timeout must be positive");
            }
            if (inputCostPerMillionTokens.signum() < 0) {
                throw new IllegalArgumentException("embedding token cost must not be negative");
            }
        }

        private static OpenAI defaults() {
            return new OpenAI(DEFAULT_ENDPOINT, "", DEFAULT_TIMEOUT, BigDecimal.ZERO);
        }
    }
}
