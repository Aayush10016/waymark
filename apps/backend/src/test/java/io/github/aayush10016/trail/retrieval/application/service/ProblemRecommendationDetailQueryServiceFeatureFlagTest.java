package io.github.aayush10016.trail.retrieval.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.aayush10016.trail.identity.domain.model.AuthenticatedPrincipal;
import io.github.aayush10016.trail.problem.domain.model.ProblemSession;
import io.github.aayush10016.trail.problem.domain.model.ProblemSessionSnapshot;
import io.github.aayush10016.trail.problem.domain.model.ProblemSessionSource;
import io.github.aayush10016.trail.problem.domain.port.ProblemSessionRepository;
import io.github.aayush10016.trail.retrieval.domain.model.RecommendationEvent;
import io.github.aayush10016.trail.retrieval.domain.port.CurrentProblemRecommendationInputRepository;
import io.github.aayush10016.trail.retrieval.domain.port.RecommendationEventRepository;
import io.github.aayush10016.trail.retrieval.domain.service.HybridSimilarityRanker;
import io.github.aayush10016.trail.retrieval.domain.service.WhyThisExplanationBuilder;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ProblemRecommendationDetailQueryServiceFeatureFlagTest {

    private static final UUID USER_ID = UUID.fromString("76000000-0000-4000-8000-000000000001");
    private static final UUID SESSION_ID = UUID.fromString("76000000-0000-4000-8000-000000000002");
    private static final AuthenticatedPrincipal PRINCIPAL = new AuthenticatedPrincipal(USER_ID);

    @Test
    void returnsNoRecommendationBeforeRetrievalWhenRequiredFlagsAreDisabledByDefault() {
        var inputRepository = new RecordingInputRepository();
        var service = new ProblemRecommendationDetailQueryService(
                new SingleProblemSessionRepository(candidateSnapshot()),
                inputRepository,
                throwingEventService(),
                throwingTopRecommendationService(),
                featureFlag -> false,
                Duration.ofSeconds(2));

        assertThat(service.findRecommendation(PRINCIPAL, SESSION_ID)).isEmpty();
        assertThat(inputRepository.called).isFalse();
    }

    private static TopProblemRecommendationService throwingTopRecommendationService() {
        return new TopProblemRecommendationService(
                new SolvedProblemLexicalSearchService(query -> {
                    throw new AssertionError("lexical retrieval must not run when recommendation gates are disabled");
                }),
                new ProblemVectorCandidateSearchService(query -> {
                    throw new AssertionError("vector retrieval must not run when recommendation gates are disabled");
                }),
                (userId, currentProblemSessionId, recommendedProblemSessionId) -> {
                    throw new AssertionError("suppression reads must not run when recommendation gates are disabled");
                },
                new HybridSimilarityRanker(),
                new WhyThisExplanationBuilder());
    }

    private static RecommendationEventService throwingEventService() {
        return new RecommendationEventService(new RecommendationEventRepository() {
            @Override
            public RecommendationEventWriteResult insertIfAbsent(RecommendationEvent event) {
                throw new AssertionError("recommendation events must not be recorded when gates are disabled");
            }

            @Override
            public Optional<RecommendationEvent> findShown(UUID userId, UUID recommendationId) {
                throw new AssertionError("recommendation events must not be read when gates are disabled");
            }
        }, Clock.systemUTC(), UUID::randomUUID);
    }

    private static ProblemSessionSnapshot candidateSnapshot() {
        var now = Instant.parse("2026-07-13T08:00:00Z");
        return new ProblemSessionSnapshot(
                SESSION_ID,
                ProblemSessionSource.MANUAL,
                ProblemSession.candidate(),
                0,
                "Spring Security CORS not working",
                null,
                null,
                now,
                null,
                now,
                now,
                null);
    }

    private static final class SingleProblemSessionRepository implements ProblemSessionRepository {

        private final ProblemSessionSnapshot snapshot;

        private SingleProblemSessionRepository(ProblemSessionSnapshot snapshot) {
            this.snapshot = snapshot;
        }

        @Override
        public void insert(AuthenticatedPrincipal principal, ProblemSessionSnapshot snapshot) {
            throw new UnsupportedOperationException("insert is not used by this query test");
        }

        @Override
        public Optional<ProblemSessionSnapshot> findById(AuthenticatedPrincipal principal, UUID problemSessionId) {
            return PRINCIPAL.equals(principal) && SESSION_ID.equals(problemSessionId)
                    ? Optional.of(snapshot)
                    : Optional.empty();
        }

        @Override
        public boolean update(AuthenticatedPrincipal principal, ProblemSessionSnapshot snapshot, long expectedVersion) {
            throw new UnsupportedOperationException("update is not used by this query test");
        }
    }

    private static final class RecordingInputRepository implements CurrentProblemRecommendationInputRepository {

        private boolean called;

        @Override
        public Optional<io.github.aayush10016.trail.retrieval.domain.model.CurrentProblemRecommendationInput> findInput(
                UUID userId, UUID problemSessionId) {
            called = true;
            throw new AssertionError("input lookup must not run when recommendation gates are disabled");
        }
    }
}
