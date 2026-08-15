package io.github.aayush10016.trail.intelligence.adapter.out.ai;

import io.github.aayush10016.trail.intelligence.domain.model.AICostMetadata;
import io.github.aayush10016.trail.intelligence.domain.model.AIProviderFailure;
import io.github.aayush10016.trail.intelligence.domain.model.AIProviderFailureCode;
import io.github.aayush10016.trail.intelligence.domain.model.AIUsageMetadata;
import io.github.aayush10016.trail.intelligence.domain.model.StructuredAIRequest;
import io.github.aayush10016.trail.intelligence.domain.model.StructuredAIResponse;
import io.github.aayush10016.trail.intelligence.domain.model.StructuredAIResult;
import io.github.aayush10016.trail.intelligence.domain.port.AIProvider;
import java.util.Objects;

/**
 * Deterministic AI provider adapter for tests and local non-production flows.
 */
public class MockAIProvider implements AIProvider {

    private static final String PROVIDER_NAME = "mock";

    private final MockAIProviderBehavior behavior;
    private final String outputJson;

    /**
     * Creates a mock provider.
     */
    public MockAIProvider(MockAIProviderBehavior behavior, String outputJson) {
        this.behavior = Objects.requireNonNull(behavior, "behavior");
        this.outputJson = requireOutputJson(outputJson);
    }

    /**
     * Creates a successful mock provider with the supplied output.
     */
    public static MockAIProvider successful(String outputJson) {
        return new MockAIProvider(MockAIProviderBehavior.SUCCESS, outputJson);
    }

    /**
     * Creates a mock provider that returns invalid-response failure.
     */
    public static MockAIProvider invalidResponse() {
        return new MockAIProvider(MockAIProviderBehavior.INVALID_RESPONSE, "{}");
    }

    /**
     * Creates a mock provider that returns timeout failure.
     */
    public static MockAIProvider timeout() {
        return new MockAIProvider(MockAIProviderBehavior.TIMEOUT, "{}");
    }

    @Override
    public StructuredAIResult generateStructured(StructuredAIRequest request) {
        Objects.requireNonNull(request, "request");
        return switch (behavior) {
            case SUCCESS -> StructuredAIResult.success(new StructuredAIResponse(
                    PROVIDER_NAME,
                    request.model(),
                    outputJson,
                    AIUsageMetadata.zero(),
                    AICostMetadata.zeroUsd()));
            case INVALID_RESPONSE -> StructuredAIResult.failure(new AIProviderFailure(
                    AIProviderFailureCode.INVALID_PROVIDER_RESPONSE,
                    false));
            case TIMEOUT -> StructuredAIResult.failure(new AIProviderFailure(
                    AIProviderFailureCode.PROVIDER_TIMEOUT,
                    true));
        };
    }

    private static String requireOutputJson(String outputJson) {
        if (outputJson == null || outputJson.isBlank()) {
            throw new IllegalArgumentException("outputJson must not be blank");
        }
        return outputJson.strip();
    }
}
