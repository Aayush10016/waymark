package io.github.aayush10016.trail.intelligence.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.aayush10016.trail.intelligence.domain.model.AICostMetadata;
import io.github.aayush10016.trail.intelligence.domain.model.AIUsageMetadata;
import io.github.aayush10016.trail.intelligence.domain.model.EmbeddingModelGeneration;
import io.github.aayush10016.trail.intelligence.domain.model.EmbeddingProviderFailure;
import io.github.aayush10016.trail.intelligence.domain.model.EmbeddingProviderFailureCode;
import io.github.aayush10016.trail.intelligence.domain.model.EmbeddingRequest;
import io.github.aayush10016.trail.intelligence.domain.model.EmbeddingResponse;
import io.github.aayush10016.trail.intelligence.domain.model.EmbeddingResult;
import io.github.aayush10016.trail.intelligence.domain.model.ProblemEmbedding;
import io.github.aayush10016.trail.intelligence.domain.model.SolvedProblemEmbeddingContext;
import io.github.aayush10016.trail.intelligence.domain.port.EmbeddingProvider;
import io.github.aayush10016.trail.intelligence.domain.port.ProblemEmbeddingRepository;
import io.github.aayush10016.trail.intelligence.domain.port.SolvedProblemEmbeddingContextRepository;
import io.github.aayush10016.trail.intelligence.domain.service.AIRedactionPolicy;
import io.github.aayush10016.trail.intelligence.domain.service.CanonicalEmbeddingDocumentBuilder;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SolvedProblemEmbeddingServiceTest {

    private static final UUID USER_ID = UUID.fromString("10000000-0000-4000-8000-000000000065");
    private static final UUID PROBLEM_SESSION_ID = UUID.fromString("20000000-0000-4000-8000-000000000065");
    private static final Instant NOW = Instant.parse("2026-07-18T10:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private final InMemoryContextRepository contextRepository = new InMemoryContextRepository();
    private final InMemoryEmbeddingRepository embeddingRepository = new InMemoryEmbeddingRepository();
    private final RecordingEmbeddingProvider embeddingProvider = new RecordingEmbeddingProvider();

    @Test
    void generatesOneSolvedSessionEmbeddingFromCanonicalDocument() {
        contextRepository.context = Optional.of(context());
        var service = service();

        var result = service.embed(USER_ID, PROBLEM_SESSION_ID);

        assertThat(result.status()).isEqualTo(SolvedProblemEmbeddingJobStatus.SUCCEEDED);
        assertThat(embeddingProvider.calls).isEqualTo(1);
        assertThat(embeddingRepository.active).isPresent();
        assertThat(embeddingRepository.active.orElseThrow().inputFingerprint()).matches("[0-9a-f]{64}");
    }

    @Test
    void reusesCachedEmbeddingWhenFingerprintAlreadyMatches() {
        contextRepository.context = Optional.of(context());
        var document = new CanonicalEmbeddingDocumentBuilder().build(context());
        embeddingRepository.active = Optional.of(activeEmbedding(document.fingerprint()));

        var result = service().embed(USER_ID, PROBLEM_SESSION_ID);

        assertThat(result.status()).isEqualTo(SolvedProblemEmbeddingJobStatus.SUCCEEDED);
        assertThat(embeddingProvider.calls).isZero();
    }

    @Test
    void returnsRetryableFailureWhenProviderTimesOut() {
        contextRepository.context = Optional.of(context());
        embeddingProvider.result = EmbeddingResult.failure(new EmbeddingProviderFailure(
                EmbeddingProviderFailureCode.PROVIDER_TIMEOUT, true));

        var result = service().embed(USER_ID, PROBLEM_SESSION_ID);

        assertThat(result.status()).isEqualTo(SolvedProblemEmbeddingJobStatus.RETRYABLE_FAILURE);
        assertThat(result.safeFailureCode()).isEqualTo("PROVIDER_TIMEOUT");
    }

    @Test
    void refusesInsufficientCanonicalDocumentWithoutProviderCall() {
        contextRepository.context = Optional.of(new SolvedProblemEmbeddingContext(
                USER_ID,
                PROBLEM_SESSION_ID,
                null,
                null,
                null,
                "short",
                null,
                List.of(),
                List.of(),
                List.of(),
                null,
                List.of()));

        var result = service().embed(USER_ID, PROBLEM_SESSION_ID);

        assertThat(result.status()).isEqualTo(SolvedProblemEmbeddingJobStatus.TERMINAL_FAILURE);
        assertThat(result.safeFailureCode()).isEqualTo("EMBEDDING_INPUT_REFUSED");
        assertThat(embeddingProvider.calls).isZero();
    }

    private SolvedProblemEmbeddingService service() {
        return new SolvedProblemEmbeddingService(
                contextRepository,
                embeddingRepository,
                embeddingProvider,
                new AIRedactionPolicy(),
                new CanonicalEmbeddingDocumentBuilder(),
                new SolvedProblemEmbeddingPolicy("solved-session", Duration.ofSeconds(30)),
                CLOCK);
    }

    private static SolvedProblemEmbeddingContext context() {
        return new SolvedProblemEmbeddingContext(
                USER_ID,
                PROBLEM_SESSION_ID,
                "Spring Security CORS not working",
                "Browser blocked localhost frontend.",
                null,
                "Allowed localhost in CorsConfiguration.",
                "CORS configuration was missing localhost.",
                List.of("Spring Security"),
                List.of("Spring"),
                List.of("cors"),
                "CONFIGURATION",
                List.of());
    }

    private static ProblemEmbedding activeEmbedding(String fingerprint) {
        return new ProblemEmbedding(
                UUID.randomUUID(),
                USER_ID,
                PROBLEM_SESSION_ID,
                "solved-session",
                1,
                "mock",
                "trail-mock-embedding",
                3,
                fingerprint,
                "ai-redaction-v1",
                List.of(0.1, 0.2, 0.3),
                true,
                null,
                NOW);
    }

    private static final class InMemoryContextRepository implements SolvedProblemEmbeddingContextRepository {

        private Optional<SolvedProblemEmbeddingContext> context = Optional.empty();

        @Override
        public Optional<SolvedProblemEmbeddingContext> findSolvedContext(UUID userId, UUID problemSessionId) {
            return context.filter(value -> value.userId().equals(userId) && value.problemSessionId().equals(problemSessionId));
        }
    }

    private static final class InMemoryEmbeddingRepository implements ProblemEmbeddingRepository {

        private final EmbeddingModelGeneration generation = new EmbeddingModelGeneration(
                UUID.randomUUID(),
                "solved-session",
                1,
                "mock",
                "trail-mock-embedding",
                3,
                true,
                NOW);
        private Optional<ProblemEmbedding> active = Optional.empty();

        @Override
        public void save(ProblemEmbedding embedding) {
            active = Optional.of(embedding);
        }

        @Override
        public void replaceActive(ProblemEmbedding embedding) {
            active = Optional.of(embedding);
        }

        @Override
        public Optional<ProblemEmbedding> findActive(
                UUID userId, UUID problemSessionId, String generationKey, int generationVersion) {
            return active.filter(value -> value.userId().equals(userId)
                    && value.problemSessionId().equals(problemSessionId)
                    && value.generationKey().equals(generationKey)
                    && value.generationVersion() == generationVersion);
        }

        @Override
        public Optional<EmbeddingModelGeneration> findActiveGeneration(String generationKey) {
            return generation.generationKey().equals(generationKey) ? Optional.of(generation) : Optional.empty();
        }
    }

    private static final class RecordingEmbeddingProvider implements EmbeddingProvider {

        private int calls;
        private EmbeddingResult result;

        @Override
        public EmbeddingResult embed(EmbeddingRequest request) {
            calls += 1;
            if (result != null) {
                return result;
            }
            return EmbeddingResult.success(new EmbeddingResponse(
                    "mock",
                    request.model(),
                    request.dimensions(),
                    request.inputFingerprint(),
                    List.of(0.1, 0.2, 0.3),
                    AIUsageMetadata.zero(),
                    AICostMetadata.zeroUsd()));
        }
    }
}
