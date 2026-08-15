package io.github.aayush10016.trail.intelligence.adapter.out.embedding;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.aayush10016.trail.intelligence.domain.model.EmbeddingProviderFailureCode;
import io.github.aayush10016.trail.intelligence.domain.model.EmbeddingRequest;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MockEmbeddingProviderTest {

    private static final String FINGERPRINT = "b".repeat(64);

    @Test
    void createsDeterministicEmbeddingWithRequestedDimensions() {
        var request = request(4);
        var result = MockEmbeddingProvider.successful().embed(request);

        assertThat(result.succeeded()).isTrue();
        assertThat(result.response().provider()).isEqualTo("mock");
        assertThat(result.response().model()).isEqualTo(request.model());
        assertThat(result.response().dimensions()).isEqualTo(4);
        assertThat(result.response().vector()).containsExactly(0.25, 0.5, 0.75, 1.0);
        assertThat(result.response().inputFingerprint()).isEqualTo(FINGERPRINT);
    }

    @Test
    void returnsSafeInvalidResponseFailure() {
        var result = MockEmbeddingProvider.invalidResponse().embed(request(3));

        assertThat(result.succeeded()).isFalse();
        assertThat(result.failure().code()).isEqualTo(EmbeddingProviderFailureCode.INVALID_PROVIDER_RESPONSE);
        assertThat(result.failure().retryable()).isFalse();
    }

    @Test
    void returnsSafeRetryableTimeoutFailure() {
        var result = MockEmbeddingProvider.timeout().embed(request(3));

        assertThat(result.succeeded()).isFalse();
        assertThat(result.failure().code()).isEqualTo(EmbeddingProviderFailureCode.PROVIDER_TIMEOUT);
        assertThat(result.failure().retryable()).isTrue();
    }

    private static EmbeddingRequest request(int dimensions) {
        return new EmbeddingRequest(
                "trail-mock-embedding",
                "Spring Security CORS solution",
                dimensions,
                FINGERPRINT,
                "embedding-policy-v1",
                Duration.ofSeconds(5),
                Map.of());
    }
}
