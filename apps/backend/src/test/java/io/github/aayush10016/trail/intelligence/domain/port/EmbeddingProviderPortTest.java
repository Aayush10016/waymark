package io.github.aayush10016.trail.intelligence.domain.port;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.aayush10016.trail.intelligence.domain.model.AICostMetadata;
import io.github.aayush10016.trail.intelligence.domain.model.AIUsageMetadata;
import io.github.aayush10016.trail.intelligence.domain.model.EmbeddingProviderFailure;
import io.github.aayush10016.trail.intelligence.domain.model.EmbeddingProviderFailureCode;
import io.github.aayush10016.trail.intelligence.domain.model.EmbeddingRequest;
import io.github.aayush10016.trail.intelligence.domain.model.EmbeddingResponse;
import io.github.aayush10016.trail.intelligence.domain.model.EmbeddingResult;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class EmbeddingProviderPortTest {

    private static final String FINGERPRINT = "a".repeat(64);

    @Test
    void validatesProviderNeutralRequestAndResponseMetadata() {
        var request = new EmbeddingRequest(
                "text-embedding-model",
                "Spring Security CORS solution",
                3,
                FINGERPRINT,
                "embedding-policy-v1",
                Duration.ofSeconds(5),
                Map.of("source", "test"));

        var response = new EmbeddingResponse(
                "provider",
                request.model(),
                request.dimensions(),
                request.inputFingerprint(),
                List.of(0.1, 0.2, 0.3),
                AIUsageMetadata.zero(),
                AICostMetadata.zeroUsd());

        assertThat(response.provider()).isEqualTo("provider");
        assertThat(response.model()).isEqualTo(request.model());
        assertThat(response.dimensions()).isEqualTo(3);
        assertThat(response.inputFingerprint()).isEqualTo(FINGERPRINT);
    }

    @Test
    void rejectsMismatchedVectorDimensionAndNonFiniteValues() {
        assertThatThrownBy(() -> new EmbeddingResponse(
                        "provider",
                        "model",
                        3,
                        FINGERPRINT,
                        List.of(0.1, 0.2),
                        AIUsageMetadata.zero(),
                        AICostMetadata.zeroUsd()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new EmbeddingResponse(
                        "provider",
                        "model",
                        1,
                        FINGERPRINT,
                        List.of(Double.NaN),
                        AIUsageMetadata.zero(),
                        AICostMetadata.zeroUsd()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void resultRequiresExactlyOneBranch() {
        var failure = new EmbeddingProviderFailure(EmbeddingProviderFailureCode.PROVIDER_TIMEOUT, true);

        assertThat(EmbeddingResult.failure(failure).succeeded()).isFalse();
        assertThatThrownBy(() -> new EmbeddingResult(null, null)).isInstanceOf(IllegalArgumentException.class);
    }
}
