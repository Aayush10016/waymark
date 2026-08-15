package io.github.aayush10016.trail.intelligence.adapter.out.embedding;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.aayush10016.trail.intelligence.domain.model.AICostMetadata;
import io.github.aayush10016.trail.intelligence.domain.model.AIUsageMetadata;
import io.github.aayush10016.trail.intelligence.domain.model.EmbeddingProviderFailure;
import io.github.aayush10016.trail.intelligence.domain.model.EmbeddingProviderFailureCode;
import io.github.aayush10016.trail.intelligence.domain.model.EmbeddingRequest;
import io.github.aayush10016.trail.intelligence.domain.model.EmbeddingResponse;
import io.github.aayush10016.trail.intelligence.domain.model.EmbeddingResult;
import io.github.aayush10016.trail.intelligence.domain.port.EmbeddingProvider;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Production HTTP adapter for OpenAI-compatible embedding generation.
 */
public class OpenAICompatibleEmbeddingProvider implements EmbeddingProvider {

    private static final String PROVIDER_NAME = "openai";
    private static final BigDecimal ONE_MILLION = new BigDecimal("1000000");

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final URI embeddingsEndpoint;
    private final String apiKey;
    private final OpenAICompatibleEmbeddingPricing pricing;

    /**
     * Creates the provider adapter.
     */
    public OpenAICompatibleEmbeddingProvider(
            HttpClient httpClient,
            ObjectMapper objectMapper,
            URI embeddingsEndpoint,
            String apiKey,
            OpenAICompatibleEmbeddingPricing pricing) {
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.embeddingsEndpoint = Objects.requireNonNull(embeddingsEndpoint, "embeddingsEndpoint");
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("apiKey must not be blank");
        }
        this.apiKey = apiKey.strip();
        this.pricing = Objects.requireNonNull(pricing, "pricing");
    }

    @Override
    public EmbeddingResult embed(EmbeddingRequest request) {
        Objects.requireNonNull(request, "request");
        try {
            var httpRequest = HttpRequest.newBuilder(embeddingsEndpoint)
                    .timeout(request.timeout())
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody(request)))
                    .build();
            var response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            return mapResponse(request, response);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return failure(EmbeddingProviderFailureCode.PROVIDER_TIMEOUT, true);
        } catch (java.net.http.HttpTimeoutException exception) {
            return failure(EmbeddingProviderFailureCode.PROVIDER_TIMEOUT, true);
        } catch (IOException exception) {
            return failure(EmbeddingProviderFailureCode.PROVIDER_UNAVAILABLE, true);
        } catch (RuntimeException exception) {
            return failure(EmbeddingProviderFailureCode.INVALID_PROVIDER_RESPONSE, false);
        }
    }

    private String requestBody(EmbeddingRequest request) throws JsonProcessingException {
        return objectMapper.writeValueAsString(Map.of(
                "model",
                request.model(),
                "input",
                request.input(),
                "dimensions",
                request.dimensions(),
                "encoding_format",
                "float"));
    }

    private EmbeddingResult mapResponse(EmbeddingRequest request, HttpResponse<String> response)
            throws JsonProcessingException {
        var statusCode = response.statusCode();
        if (statusCode == 408 || statusCode == 504) {
            return failure(EmbeddingProviderFailureCode.PROVIDER_TIMEOUT, true);
        }
        if (statusCode == 429 || statusCode >= 500) {
            return failure(EmbeddingProviderFailureCode.PROVIDER_UNAVAILABLE, true);
        }
        if (statusCode >= 400) {
            return failure(EmbeddingProviderFailureCode.PROVIDER_REJECTED, false);
        }

        var root = objectMapper.readTree(response.body());
        var vector = extractVector(root.path("data"));
        var usage = usage(root.path("usage"));
        return EmbeddingResult.success(new EmbeddingResponse(
                PROVIDER_NAME,
                model(root, request.model()),
                request.dimensions(),
                request.inputFingerprint(),
                vector,
                usage,
                pricing.estimate(usage)));
    }

    private static String model(JsonNode root, String fallbackModel) {
        var model = root.path("model");
        return model.isTextual() && !model.asText().isBlank() ? model.asText() : fallbackModel;
    }

    private static List<Double> extractVector(JsonNode dataNode) {
        if (!dataNode.isArray() || dataNode.isEmpty()) {
            throw new IllegalArgumentException("embedding response data is empty");
        }
        var embeddingNode = dataNode.get(0).path("embedding");
        if (!embeddingNode.isArray() || embeddingNode.isEmpty()) {
            throw new IllegalArgumentException("embedding response vector is empty");
        }
        var values = new java.util.ArrayList<Double>();
        for (var valueNode : embeddingNode) {
            if (!valueNode.isNumber()) {
                throw new IllegalArgumentException("embedding vector contains non-number");
            }
            values.add(valueNode.asDouble());
        }
        return List.copyOf(values);
    }

    private static AIUsageMetadata usage(JsonNode usageNode) {
        var promptTokens = Math.max(0L, usageNode.path("prompt_tokens").asLong(0L));
        var totalTokens = Math.max(promptTokens, usageNode.path("total_tokens").asLong(0L));
        return new AIUsageMetadata(promptTokens, 0, totalTokens);
    }

    private static EmbeddingResult failure(EmbeddingProviderFailureCode code, boolean retryable) {
        return EmbeddingResult.failure(new EmbeddingProviderFailure(code, retryable));
    }

    /**
     * Provider pricing metadata configured externally.
     */
    public record OpenAICompatibleEmbeddingPricing(BigDecimal inputCostPerMillionTokens) {

        /**
         * Creates validated pricing.
         */
        public OpenAICompatibleEmbeddingPricing {
            Objects.requireNonNull(inputCostPerMillionTokens, "inputCostPerMillionTokens");
            if (inputCostPerMillionTokens.signum() < 0) {
                throw new IllegalArgumentException("token costs must not be negative");
            }
        }

        /**
         * Estimates embedding cost using configured per-million-token rate.
         */
        public AICostMetadata estimate(AIUsageMetadata usage) {
            Objects.requireNonNull(usage, "usage");
            var cost = inputCostPerMillionTokens
                    .multiply(BigDecimal.valueOf(usage.inputTokens()))
                    .divide(ONE_MILLION, 12, RoundingMode.HALF_UP);
            return new AICostMetadata(cost, java.util.Currency.getInstance("USD"));
        }
    }
}
