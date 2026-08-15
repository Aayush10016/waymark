package io.github.aayush10016.trail.retrieval.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.aayush10016.trail.identity.domain.model.AuthenticatedPrincipal;
import io.github.aayush10016.trail.retrieval.domain.model.RecommendationEvent;
import io.github.aayush10016.trail.retrieval.domain.model.RecommendationEventOutcome;
import io.github.aayush10016.trail.retrieval.domain.model.RecommendationEventProvenance;
import io.github.aayush10016.trail.retrieval.domain.model.RecommendationEvidenceReference;
import io.github.aayush10016.trail.retrieval.domain.model.SimilarityEvidenceCode;
import io.github.aayush10016.trail.retrieval.domain.port.RecommendationEventRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RecommendationEventServiceTest {

    private static final UUID USER_ID = UUID.fromString("78000000-0000-4000-8000-000000000001");
    private static final UUID RECOMMENDATION_ID = UUID.fromString("78000000-0000-4000-8000-000000000002");
    private static final UUID CURRENT_SESSION_ID = UUID.fromString("78000000-0000-4000-8000-000000000003");
    private static final UUID RECOMMENDED_SESSION_ID = UUID.fromString("78000000-0000-4000-8000-000000000004");
    private static final UUID EVENT_ID = UUID.fromString("78000000-0000-4000-8000-000000000005");
    private static final AuthenticatedPrincipal PRINCIPAL = new AuthenticatedPrincipal(USER_ID);

    @Test
    void refusesClientOutcomeBeforeServerDerivedShownEventExists() {
        var service = new RecommendationEventService(new InMemoryRecommendationEventRepository(), fixedClock(), () -> EVENT_ID);

        assertThatThrownBy(() -> service.recordClientOutcome(PRINCIPAL, RECOMMENDATION_ID, RecommendationEventOutcome.HELPFUL))
                .isInstanceOf(ProblemRecommendationNotFoundException.class);
    }

    @Test
    void recordsClientOutcomeFromShownEventAndReportsDuplicatesIdempotently() {
        var repository = new InMemoryRecommendationEventRepository();
        repository.insertIfAbsent(shownEvent());
        var service = new RecommendationEventService(repository, fixedClock(), () -> EVENT_ID);

        var first = service.recordClientOutcome(PRINCIPAL, RECOMMENDATION_ID, RecommendationEventOutcome.OPENED);
        var duplicate = service.recordClientOutcome(PRINCIPAL, RECOMMENDATION_ID, RecommendationEventOutcome.OPENED);

        assertThat(first.status().name()).isEqualTo("ACCEPTED");
        assertThat(duplicate.status().name()).isEqualTo("DUPLICATE");
        assertThat(repository.events)
                .extracting(RecommendationEvent::outcome)
                .containsExactly(RecommendationEventOutcome.SHOWN, RecommendationEventOutcome.OPENED);
    }

    @Test
    void rejectsClientSubmittedShownOutcome() {
        var service = new RecommendationEventService(new InMemoryRecommendationEventRepository(), fixedClock(), () -> EVENT_ID);

        assertThatThrownBy(() -> service.recordClientOutcome(PRINCIPAL, RECOMMENDATION_ID, RecommendationEventOutcome.SHOWN))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static RecommendationEvent shownEvent() {
        return new RecommendationEvent(
                UUID.fromString("78000000-0000-4000-8000-000000000006"),
                USER_ID,
                RECOMMENDATION_ID,
                CURRENT_SESSION_ID,
                RECOMMENDED_SESSION_ID,
                RecommendationEventOutcome.SHOWN,
                List.of(RECOMMENDED_SESSION_ID),
                List.of(RecommendationEvidenceReference.session(RECOMMENDED_SESSION_ID, SimilarityEvidenceCode.ERROR_PHRASE)),
                new RecommendationEventProvenance("solved-session", 1, "mock", "trail-mock-embedding", "9".repeat(64)),
                "hybrid-similarity-v1",
                Instant.parse("2026-07-13T08:00:00Z"));
    }

    private static Clock fixedClock() {
        return Clock.fixed(Instant.parse("2026-07-13T09:00:00Z"), ZoneOffset.UTC);
    }

    private static final class InMemoryRecommendationEventRepository implements RecommendationEventRepository {

        private final List<RecommendationEvent> events = new ArrayList<>();

        @Override
        public RecommendationEventWriteResult insertIfAbsent(RecommendationEvent event) {
            var duplicate = events.stream()
                    .anyMatch(existing -> existing.userId().equals(event.userId())
                            && existing.recommendationId().equals(event.recommendationId())
                            && existing.outcome() == event.outcome());
            if (duplicate) {
                return RecommendationEventWriteResult.DUPLICATE;
            }
            events.add(event);
            return RecommendationEventWriteResult.INSERTED;
        }

        @Override
        public Optional<RecommendationEvent> findShown(UUID userId, UUID recommendationId) {
            return events.stream()
                    .filter(event -> event.userId().equals(userId))
                    .filter(event -> event.recommendationId().equals(recommendationId))
                    .filter(event -> event.outcome() == RecommendationEventOutcome.SHOWN)
                    .findFirst();
        }
    }
}
