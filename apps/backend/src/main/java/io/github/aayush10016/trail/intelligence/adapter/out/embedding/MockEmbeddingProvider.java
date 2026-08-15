package io.github.aayush10016.trail.intelligence.adapter.out.embedding;

import io.github.aayush10016.trail.intelligence.domain.model.AICostMetadata;
import io.github.aayush10016.trail.intelligence.domain.model.AIUsageMetadata;
import io.github.aayush10016.trail.intelligence.domain.model.EmbeddingProviderFailure;
import io.github.aayush10016.trail.intelligence.domain.model.EmbeddingProviderFailureCode;
import io.github.aayush10016.trail.intelligence.domain.model.EmbeddingRequest;
import io.github.aayush10016.trail.intelligence.domain.model.EmbeddingResponse;
import io.github.aayush10016.trail.intelligence.domain.model.EmbeddingResult;
import io.github.aayush10016.trail.intelligence.domain.port.EmbeddingProvider;
import java.util.Objects;
import java.util.stream.IntStream;

/**
 * Deterministic embedding provider adapter for tests and local non-production flows.
 */
public class MockEmbeddingProvider implements EmbeddingProvider {

    private static final String PROVIDER_NAME = "mock";

    private final MockEmbeddingProviderBehavior behavior;

    /**
     * Creates a mock embedding provider.
     */
    public MockEmbeddingProvider(MockEmbeddingProviderBehavior behavior) {
        this.behavior = Objects.requireNonNull(behavior, "behavior");
    }

    /**
     * Creates a successful mock embedding provider.
     */
    public static MockEmbeddingProvider successful() {
        return new MockEmbeddingProvider(MockEmbeddingProviderBehavior.SUCCESS);
    }

    /**
     * Creates an invalid-response mock embedding provider.
     */
    public static MockEmbeddingProvider invalidResponse() {
        return new MockEmbeddingProvider(MockEmbeddingProviderBehavior.INVALID_RESPONSE);
    }

    /**
     * Creates a timeout mock embedding provider.
     */
    public static MockEmbeddingProvider timeout() {
        return new MockEmbeddingProvider(MockEmbeddingProviderBehavior.TIMEOUT);
    }

    @Override
    public EmbeddingResult embed(EmbeddingRequest request) {
        Objects.requireNonNull(request, "request");
        return switch (behavior) {
            case SUCCESS -> EmbeddingResult.success(new EmbeddingResponse(
                    PROVIDER_NAME,
                    request.model(),
                    request.dimensions(),
                    request.inputFingerprint(),
                    deterministicVector(request.dimensions()),
                    AIUsageMetadata.zero(),
                    AICostMetadata.zeroUsd()));
            case INVALID_RESPONSE -> EmbeddingResult.failure(new EmbeddingProviderFailure(
                    EmbeddingProviderFailureCode.INVALID_PROVIDER_RESPONSE,
                    false));
            case TIMEOUT -> EmbeddingResult.failure(new EmbeddingProviderFailure(
                    EmbeddingProviderFailureCode.PROVIDER_TIMEOUT,
                    true));
        };
    }

    private static java.util.List<Double> deterministicVector(int dimensions) {
        return IntStream.range(0, dimensions)
                .mapToObj(index -> (index + 1) / (double) dimensions)
                .toList();
    }
}
