package io.github.aayush10016.trail.intelligence.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Currency;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class StructuredAIContractTest {

    @Test
    void requestMetadataIsImmutable() {
        var metadata = new HashMap<String, String>();
        metadata.put("prompt_key", "problem-summary");

        var request = new StructuredAIRequest(
                "trail-test-model",
                "Return JSON only.",
                "Summarize this safe input.",
                "{\"type\":\"object\"}",
                Duration.ofSeconds(5),
                metadata);
        metadata.put("prompt_key", "changed");

        assertThat(request.metadata()).containsExactly(Map.entry("prompt_key", "problem-summary"));
        assertThatThrownBy(() -> request.metadata().put("new", "value"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void validatesRequestBounds() {
        assertThatThrownBy(() -> new StructuredAIRequest(
                        " ",
                        "Return JSON.",
                        "Input",
                        "{\"type\":\"object\"}",
                        Duration.ofSeconds(5),
                        Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("model");
        assertThatThrownBy(() -> new StructuredAIRequest(
                        "model",
                        "Return JSON.",
                        "Input",
                        "{\"type\":\"object\"}",
                        Duration.ZERO,
                        Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("timeout");
    }

    @Test
    void resultRequiresExactlyOneBranch() {
        var response = new StructuredAIResponse(
                "mock",
                "model",
                "{}",
                AIUsageMetadata.zero(),
                AICostMetadata.zeroUsd());
        var failure = new AIProviderFailure(AIProviderFailureCode.PROVIDER_TIMEOUT, true);

        assertThat(StructuredAIResult.success(response).succeeded()).isTrue();
        assertThat(StructuredAIResult.failure(failure).succeeded()).isFalse();
        assertThatThrownBy(() -> new StructuredAIResult(response, failure))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new StructuredAIResult(null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void validatesUsageAndCostMetadata() {
        assertThatThrownBy(() -> new AIUsageMetadata(2, 2, 3))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("totalTokens");
        assertThatThrownBy(() -> new AICostMetadata(new BigDecimal("-0.01"), Currency.getInstance("USD")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("estimatedCost");
    }
}
