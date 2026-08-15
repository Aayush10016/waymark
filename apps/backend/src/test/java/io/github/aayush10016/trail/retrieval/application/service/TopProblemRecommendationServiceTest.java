package io.github.aayush10016.trail.retrieval.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.aayush10016.trail.identity.domain.model.AuthenticatedPrincipal;
import io.github.aayush10016.trail.retrieval.application.query.FindTopProblemRecommendationQuery;
import io.github.aayush10016.trail.retrieval.domain.model.ProblemRecommendationMode;
import io.github.aayush10016.trail.retrieval.domain.model.ProblemRecommendationStrength;
import io.github.aayush10016.trail.retrieval.domain.model.ProblemVectorCandidate;
import io.github.aayush10016.trail.retrieval.domain.model.SearchEvidenceCode;
import io.github.aayush10016.trail.retrieval.domain.model.SolvedProblemSearchQuery;
import io.github.aayush10016.trail.retrieval.domain.model.SolvedProblemSearchResult;
import io.github.aayush10016.trail.retrieval.domain.port.ProblemVectorCandidateRepository;
import io.github.aayush10016.trail.retrieval.domain.port.RecommendationSuppressionRepository;
import io.github.aayush10016.trail.retrieval.domain.port.SolvedProblemLexicalSearchRepository;
import io.github.aayush10016.trail.retrieval.domain.service.HybridSimilarityRanker;
import io.github.aayush10016.trail.retrieval.domain.service.WhyThisExplanationBuilder;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TopProblemRecommendationServiceTest {

    private static final AuthenticatedPrincipal PRINCIPAL =
            new AuthenticatedPrincipal(UUID.fromString("74000000-0000-4000-8000-000000000001"));
    private static final UUID CURRENT_SESSION_ID = UUID.fromString("74000000-0000-4000-8000-000000000010");
    private static final UUID STRONG_SESSION_ID = UUID.fromString("74000000-0000-4000-8000-000000000011");
    private static final UUID POSSIBLE_SESSION_ID = UUID.fromString("74000000-0000-4000-8000-000000000012");

    private final InMemoryLexicalRepository lexicalRepository = new InMemoryLexicalRepository();
    private final InMemoryVectorRepository vectorRepository = new InMemoryVectorRepository();
    private final InMemorySuppressionRepository suppressionRepository = new InMemorySuppressionRepository();
    private final TopProblemRecommendationService service = new TopProblemRecommendationService(
            new SolvedProblemLexicalSearchService(lexicalRepository),
            new ProblemVectorCandidateSearchService(vectorRepository),
            suppressionRepository,
            new HybridSimilarityRanker(),
            new WhyThisExplanationBuilder());

    @Test
    void returnsOneStrongRecommendationForCurrentCandidate() {
        lexicalRepository.results = List.of(searchResult(STRONG_SESSION_ID, 14.0, SearchEvidenceCode.ERROR_PHRASE));
        vectorRepository.candidates = List.of(vectorCandidate(STRONG_SESSION_ID, 0.05));

        var result = service.findTop(PRINCIPAL, query(ProblemRecommendationMode.PROACTIVE));

        assertThat(result.recommendation()).isPresent();
        assertThat(result.recommendation().orElseThrow().currentProblemSessionId()).isEqualTo(CURRENT_SESSION_ID);
        assertThat(result.recommendation().orElseThrow().recommendedProblemSessionId()).isEqualTo(STRONG_SESSION_ID);
        assertThat(result.recommendation().orElseThrow().strength()).isEqualTo(ProblemRecommendationStrength.STRONG);
        assertThat(result.recommendation().orElseThrow().explanation().messages())
                .allSatisfy(message -> assertThat(message.problemSessionId()).isEqualTo(STRONG_SESSION_ID));
    }

    @Test
    void returnsNoMatchWhenOnlyPossibleCandidateExistsInProactiveMode() {
        lexicalRepository.results = List.of(searchResult(POSSIBLE_SESSION_ID, 8.0, SearchEvidenceCode.ERROR_PHRASE));
        vectorRepository.candidates = List.of(vectorCandidate(POSSIBLE_SESSION_ID, 0.34));

        var result = service.findTop(PRINCIPAL, query(ProblemRecommendationMode.PROACTIVE));

        assertThat(result.recommendation()).isEmpty();
    }

    @Test
    void returnsPossibleRecommendationOnlyAfterRecallMode() {
        lexicalRepository.results = List.of(searchResult(POSSIBLE_SESSION_ID, 8.0, SearchEvidenceCode.ERROR_PHRASE));
        vectorRepository.candidates = List.of(vectorCandidate(POSSIBLE_SESSION_ID, 0.34));

        var result = service.findTop(PRINCIPAL, query(ProblemRecommendationMode.RECALL));

        assertThat(result.recommendation()).isPresent();
        assertThat(result.recommendation().orElseThrow().strength()).isEqualTo(ProblemRecommendationStrength.POSSIBLE);
    }

    @Test
    void suppressesDismissedOrNotRelatedPairsAndPreservesOwnershipBoundary() {
        lexicalRepository.results = List.of(
                searchResult(STRONG_SESSION_ID, 14.0, SearchEvidenceCode.ERROR_PHRASE),
                searchResult(POSSIBLE_SESSION_ID, 13.5, SearchEvidenceCode.ERROR_PHRASE));
        vectorRepository.candidates =
                List.of(vectorCandidate(STRONG_SESSION_ID, 0.05), vectorCandidate(POSSIBLE_SESSION_ID, 0.06));
        suppressionRepository.suppressedPairs.add(pair(PRINCIPAL.userId(), CURRENT_SESSION_ID, STRONG_SESSION_ID));

        var result = service.findTop(PRINCIPAL, query(ProblemRecommendationMode.PROACTIVE));

        assertThat(result.recommendation()).isPresent();
        assertThat(result.recommendation().orElseThrow().recommendedProblemSessionId()).isEqualTo(POSSIBLE_SESSION_ID);
        assertThat(suppressionRepository.lastUserId).contains(PRINCIPAL.userId());
    }

    @Test
    void rejectsInvalidQueriesAndNeverReturnsTheCurrentSessionAsRecommendation() {
        lexicalRepository.results = List.of(searchResult(CURRENT_SESSION_ID, 20.0, SearchEvidenceCode.ERROR_PHRASE));
        vectorRepository.candidates = List.of(vectorCandidate(CURRENT_SESSION_ID, 0.01));

        assertThat(service.findTop(PRINCIPAL, query(ProblemRecommendationMode.PROACTIVE)).recommendation()).isEmpty();
        assertThatThrownBy(() -> new FindTopProblemRecommendationQuery(
                        CURRENT_SESSION_ID,
                        "spring cors",
                        List.of(),
                        List.of(),
                        "solved-session",
                        List.of(),
                        10,
                        Duration.ofSeconds(2),
                        ProblemRecommendationMode.PROACTIVE))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static FindTopProblemRecommendationQuery query(ProblemRecommendationMode mode) {
        return new FindTopProblemRecommendationQuery(
                CURRENT_SESSION_ID,
                "Spring Security CORS not working",
                List.of("Spring Security"),
                List.of("CORS"),
                "solved-session",
                List.of(0.1, 0.2, 0.3),
                10,
                Duration.ofSeconds(2),
                mode);
    }

    private static SolvedProblemSearchResult searchResult(
            UUID problemSessionId, double score, SearchEvidenceCode... evidenceCodes) {
        return new SolvedProblemSearchResult(
                problemSessionId,
                "Spring Security CORS not working",
                "Allowed localhost in CorsConfiguration.",
                "Summary",
                List.of("Spring Security"),
                List.of("CORS"),
                "CONFIGURATION",
                score,
                List.of(evidenceCodes),
                Instant.parse("2026-07-18T10:00:00Z"));
    }

    private static ProblemVectorCandidate vectorCandidate(UUID problemSessionId, double distance) {
        return new ProblemVectorCandidate(
                problemSessionId,
                "solved-session",
                1,
                "mock",
                "trail-mock-embedding",
                "8".repeat(64),
                distance);
    }

    private static String pair(UUID userId, UUID currentProblemSessionId, UUID recommendedProblemSessionId) {
        return userId + ":" + currentProblemSessionId + ":" + recommendedProblemSessionId;
    }

    private static final class InMemoryLexicalRepository implements SolvedProblemLexicalSearchRepository {

        private List<SolvedProblemSearchResult> results = List.of();

        @Override
        public List<SolvedProblemSearchResult> search(SolvedProblemSearchQuery query) {
            return results;
        }
    }

    private static final class InMemoryVectorRepository implements ProblemVectorCandidateRepository {

        private List<ProblemVectorCandidate> candidates = List.of();

        @Override
        public List<ProblemVectorCandidate> findCandidates(
                io.github.aayush10016.trail.retrieval.domain.model.ProblemVectorCandidateQuery query) {
            return candidates;
        }
    }

    private static final class InMemorySuppressionRepository implements RecommendationSuppressionRepository {

        private final Set<String> suppressedPairs = new HashSet<>();
        private Optional<UUID> lastUserId = Optional.empty();

        @Override
        public boolean isSuppressed(UUID userId, UUID currentProblemSessionId, UUID recommendedProblemSessionId) {
            lastUserId = Optional.of(userId);
            return suppressedPairs.contains(pair(userId, currentProblemSessionId, recommendedProblemSessionId));
        }
    }
}
