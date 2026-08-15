package io.github.aayush10016.trail.intelligence.adapter.out.ai;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.aayush10016.trail.intelligence.domain.model.AIProviderFailureCode;
import io.github.aayush10016.trail.intelligence.domain.model.StructuredAIRequest;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MockAIProviderTest {

    @Test
    void supportsSuccessfulStructuredResponse() {
        var provider = MockAIProvider.successful("{\"summary\":\"done\"}");

        var result = provider.generateStructured(request());

        assertThat(result.succeeded()).isTrue();
        assertThat(result.response()).satisfies(response -> {
            assertThat(response.provider()).isEqualTo("mock");
            assertThat(response.model()).isEqualTo("trail-test-model");
            assertThat(response.outputJson()).isEqualTo("{\"summary\":\"done\"}");
            assertThat(response.usage().totalTokens()).isZero();
            assertThat(response.cost().estimatedCost()).isZero();
        });
    }

    @Test
    void supportsInvalidResponseFailure() {
        var provider = MockAIProvider.invalidResponse();

        var result = provider.generateStructured(request());

        assertThat(result.succeeded()).isFalse();
        assertThat(result.failure()).satisfies(failure -> {
            assertThat(failure.code()).isEqualTo(AIProviderFailureCode.INVALID_PROVIDER_RESPONSE);
            assertThat(failure.retryable()).isFalse();
        });
    }

    @Test
    void supportsTimeoutFailure() {
        var provider = MockAIProvider.timeout();

        var result = provider.generateStructured(request());

        assertThat(result.succeeded()).isFalse();
        assertThat(result.failure()).satisfies(failure -> {
            assertThat(failure.code()).isEqualTo(AIProviderFailureCode.PROVIDER_TIMEOUT);
            assertThat(failure.retryable()).isTrue();
        });
    }

    private static StructuredAIRequest request() {
        return new StructuredAIRequest(
                "trail-test-model",
                "Return JSON only.",
                "Safe input",
                "{\"type\":\"object\"}",
                Duration.ofSeconds(5),
                Map.of("prompt_key", "test"));
    }
}
