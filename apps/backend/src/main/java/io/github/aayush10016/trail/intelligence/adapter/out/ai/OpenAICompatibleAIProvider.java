package io.github.aayush10016.trail.intelligence.adapter.out.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.aayush10016.trail.intelligence.domain.model.AICostMetadata;
import io.github.aayush10016.trail.intelligence.domain.model.AIProviderFailure;
import io.github.aayush10016.trail.intelligence.domain.model.AIProviderFailureCode;
import io.github.aayush10016.trail.intelligence.domain.model.AIUsageMetadata;
import io.github.aayush10016.trail.intelligence.domain.model.StructuredAIRequest;
import io.github.aayush10016.trail.intelligence.domain.model.StructuredAIResponse;
import io.github.aayush10016.trail.intelligence.domain.model.StructuredAIResult;
import io.github.aayush10016.trail.intelligence.domain.port.AIProvider;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeoutException;

/**
 * Production HTTP adapter for OpenAI Responses-compatible structured generation.
 */
public class OpenAICompatibleAIProvider implements AIProvider {

    private static final String PROVIDER_NAME = "openai";
    private static final BigDecimal ONE_MILLION = new BigDecimal("1000000");

    private final String apiKey;
    private final URI responsesEndpoint;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final OpenAICompatiblePricing pricing;

    /**
     * Creates the provider adapter.
     */
    public OpenAICompatibleAIProvider(
            HttpClient httpClient,
            ObjectMapper objectMapper,
            URI responsesEndpoint,
            String apiKey,
            OpenAICompatiblePricing pricing) {
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.responsesEndpoint = Objects.requireNonNull(responsesEndpoint, "responsesEndpoint");
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("apiKey must not be blank");
        }
        this.apiKey = apiKey.strip();
        this.pricing = Objects.requireNonNull(pricing, "pricing");
    }

    @Override
    public StructuredAIResult generateStructured(StructuredAIRequest request) {
        Objects.requireNonNull(request, "request");
        try {
            var httpRequest = HttpRequest.newBuilder(responsesEndpoint)
                    .timeout(request.timeout())
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody(request)))
                    .build();
            var response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            return mapResponse(request, response);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return failure(AIProviderFailureCode.PROVIDER_TIMEOUT, true);
        } catch (java.net.http.HttpTimeoutException exception) {
            return failure(AIProviderFailureCode.PROVIDER_TIMEOUT, true);
        } catch (IOException exception) {
            return failure(AIProviderFailureCode.PROVIDER_UNAVAILABLE, true);
        } catch (RuntimeException exception) {
            return failure(AIProviderFailureCode.INVALID_PROVIDER_RESPONSE, false);
        }
    }

    private String requestBody(StructuredAIRequest request) throws JsonProcessingException {
        var schema = objectMapper.readTree(request.responseSchemaJson());
        var body = Map.of(
                "model",
                request.model(),
                "store",
                false,
                "input",
                java.util.List.of(
                        Map.of("role", "system", "content", request.systemInstruction()),
                        Map.of("role", "user", "content", request.userInput())),
                "text",
                Map.of(
                        "format",
                        Map.of(
                                "type",
                                "json_schema",
                                "name",
                                "trail_structured_output",
                                "strict",
                                true,
                                "schema",
                                schema)));
        return objectMapper.writeValueAsString(body);
    }

    private StructuredAIResult mapResponse(StructuredAIRequest request, HttpResponse<String> response) throws JsonProcessingException {
        var statusCode = response.statusCode();
        if (statusCode == 408 || statusCode == 504) {
            return failure(AIProviderFailureCode.PROVIDER_TIMEOUT, true);
        }
        if (statusCode == 429 || statusCode >= 500) {
            return failure(AIProviderFailureCode.PROVIDER_UNAVAILABLE, true);
        }
        if (statusCode >= 400) {
            return failure(AIProviderFailureCode.PROVIDER_REJECTED, false);
        }

        var root = objectMapper.readTree(response.body());
        var outputJson = extractOutputText(root)
                .filter(OpenAICompatibleAIProvider::isJsonObject)
                .orElseThrow(() -> new IllegalArgumentException("response did not contain structured output"));
        var usage = usage(root.path("usage"));
        return StructuredAIResult.success(new StructuredAIResponse(
                PROVIDER_NAME,
                request.model(),
                outputJson,
                usage,
                pricing.estimate(usage)));
    }

    private static Optional<String> extractOutputText(JsonNode root) {
        var directOutputText = root.path("output_text");
        if (directOutputText.isTextual()) {
            return Optional.of(directOutputText.asText());
        }

        for (var output : root.path("output")) {
            for (var content : output.path("content")) {
                var text = content.path("text");
                if (text.isTextual()) {
                    return Optional.of(text.asText());
                }
            }
        }
        return Optional.empty();
    }

    private static AIUsageMetadata usage(JsonNode usageNode) {
        var inputTokens = Math.max(0L, usageNode.path("input_tokens").asLong(0L));
        var outputTokens = Math.max(0L, usageNode.path("output_tokens").asLong(0L));
        var totalTokens = Math.max(inputTokens + outputTokens, usageNode.path("total_tokens").asLong(0L));
        return new AIUsageMetadata(inputTokens, outputTokens, totalTokens);
    }

    private static boolean isJsonObject(String value) {
        var trimmed = value.strip();
        return trimmed.startsWith("{") && trimmed.endsWith("}");
    }

    private static StructuredAIResult failure(AIProviderFailureCode code, boolean retryable) {
        return StructuredAIResult.failure(new AIProviderFailure(code, retryable));
    }

    /**
     * Provider pricing metadata configured externally.
     */
    public record OpenAICompatiblePricing(BigDecimal inputCostPerMillionTokens, BigDecimal outputCostPerMillionTokens) {

        /**
         * Creates validated pricing.
         */
        public OpenAICompatiblePricing {
            Objects.requireNonNull(inputCostPerMillionTokens, "inputCostPerMillionTokens");
            Objects.requireNonNull(outputCostPerMillionTokens, "outputCostPerMillionTokens");
            if (inputCostPerMillionTokens.signum() < 0 || outputCostPerMillionTokens.signum() < 0) {
                throw new IllegalArgumentException("token costs must not be negative");
            }
        }

        /**
         * Estimates total cost using configured per-million-token rates.
         */
        public AICostMetadata estimate(AIUsageMetadata usage) {
            Objects.requireNonNull(usage, "usage");
            var inputCost = inputCostPerMillionTokens
                    .multiply(BigDecimal.valueOf(usage.inputTokens()))
                    .divide(ONE_MILLION, 12, RoundingMode.HALF_UP);
            var outputCost = outputCostPerMillionTokens
                    .multiply(BigDecimal.valueOf(usage.outputTokens()))
                    .divide(ONE_MILLION, 12, RoundingMode.HALF_UP);
            return new AICostMetadata(inputCost.add(outputCost), java.util.Currency.getInstance("USD"));
        }
    }
}
