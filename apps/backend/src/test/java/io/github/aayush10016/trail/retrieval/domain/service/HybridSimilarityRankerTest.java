package io.github.aayush10016.trail.retrieval.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.aayush10016.trail.retrieval.domain.model.HybridSimilarityMatch;
import io.github.aayush10016.trail.retrieval.domain.model.HybridSimilarityPolicy;
import io.github.aayush10016.trail.retrieval.domain.model.ProblemVectorCandidate;
import io.github.aayush10016.trail.retrieval.domain.model.SearchEvidenceCode;
import io.github.aayush10016.trail.retrieval.domain.model.SimilarityEvidenceCode;
import io.github.aayush10016.trail.retrieval.domain.model.SolvedProblemSearchResult;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class HybridSimilarityRankerTest {

    private static final UUID POSITIVE_SESSION_ID = UUID.fromString("50000000-0000-4000-8000-000000000069");
    private static final UUID SAME_TECH_SESSION_ID = UUID.fromString("50000000-0000-4000-8000-000000000070");
    private static final UUID FAR_SESSION_ID = UUID.fromString("50000000-0000-4000-8000-000000000071");

    private final HybridSimilarityRanker ranker = new HybridSimilarityRanker();
    private final HybridSimilarityPolicy policy = HybridSimilarityPolicy.conservativeV1();

    @Test
    void ranksCandidateWhenVectorSimilarityHasSupportingEvidenceAndRecordsAlgorithmVersion() {
        var matches = ranker.rank(
                List.of(searchResult(
                        POSITIVE_SESSION_ID,
                        12.0,
                        SearchEvidenceCode.LEXICAL_TEXT,
                        SearchEvidenceCode.ERROR_PHRASE,
                        SearchEvidenceCode.TECHNOLOGY)),
                List.of(vectorCandidate(POSITIVE_SESSION_ID, 0.05)),
                policy);

        assertThat(matches).hasSize(1);
        assertThat(matches.get(0).problemSessionId()).isEqualTo(POSITIVE_SESSION_ID);
        assertThat(matches.get(0).algorithmVersion()).isEqualTo("hybrid-similarity-v1");
        assertThat(matches.get(0).evidenceCodes())
                .containsExactly(
                        SimilarityEvidenceCode.VECTOR_DISTANCE,
                        SimilarityEvidenceCode.LEXICAL_TEXT,
                        SimilarityEvidenceCode.ERROR_PHRASE,
                        SimilarityEvidenceCode.TECHNOLOGY);
    }

    @Test
    void rejectsSameTechnologyHardNegativeWithoutSupportingEvidence() {
        var matches = ranker.rank(
                List.of(searchResult(SAME_TECH_SESSION_ID, 9.0, SearchEvidenceCode.TECHNOLOGY)),
                List.of(vectorCandidate(SAME_TECH_SESSION_ID, 0.01)),
                policy);

        assertThat(matches).isEmpty();
    }

    @Test
    void rejectsVectorOnlyCandidatesAndDistanceFailures() {
        var vectorOnlyMatches = ranker.rank(
                List.of(),
                List.of(vectorCandidate(POSITIVE_SESSION_ID, 0.01)),
                policy);
        var distanceFailureMatches = ranker.rank(
                List.of(searchResult(FAR_SESSION_ID, 18.0, SearchEvidenceCode.ERROR_PHRASE)),
                List.of(vectorCandidate(FAR_SESSION_ID, 0.40)),
                policy);

        assertThat(vectorOnlyMatches).isEmpty();
        assertThat(distanceFailureMatches).isEmpty();
    }

    @Test
    void ranksDeterministicallyByHybridScoreDistanceAndId() {
        var firstId = UUID.fromString("50000000-0000-4000-8000-000000000001");
        var secondId = UUID.fromString("50000000-0000-4000-8000-000000000002");
        var thirdId = UUID.fromString("50000000-0000-4000-8000-000000000003");

        var matches = ranker.rank(
                List.of(
                        searchResult(thirdId, 10.0, SearchEvidenceCode.ERROR_PHRASE),
                        searchResult(firstId, 15.0, SearchEvidenceCode.ERROR_PHRASE),
                        searchResult(secondId, 15.0, SearchEvidenceCode.ERROR_PHRASE)),
                List.of(
                        vectorCandidate(thirdId, 0.02),
                        vectorCandidate(secondId, 0.05),
                        vectorCandidate(firstId, 0.05)),
                policy);

        assertThat(matches)
                .extracting(HybridSimilarityMatch::problemSessionId)
                .containsExactly(firstId, secondId, thirdId);
    }

    @Test
    void validatesPolicyAndImmutableMatchEvidence() {
        var match = ranker.rank(
                        List.of(searchResult(POSITIVE_SESSION_ID, 12.0, SearchEvidenceCode.LEXICAL_TEXT)),
                        List.of(vectorCandidate(POSITIVE_SESSION_ID, 0.05)),
                        policy)
                .get(0);

        assertThatThrownBy(() -> match.evidenceCodes().add(SimilarityEvidenceCode.ERROR_PHRASE))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> new HybridSimilarityPolicy("", 0.3, 0.6, 0.7, 0.3))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new HybridSimilarityPolicy("bad", 1.1, 0.6, 0.7, 0.3))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new HybridSimilarityPolicy("bad", 0.3, 0.6, 0.0, 0.3))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static SolvedProblemSearchResult searchResult(
            UUID problemSessionId, double score, SearchEvidenceCode... evidenceCodes) {
        return new SolvedProblemSearchResult(
                problemSessionId,
                "Solved problem",
                "Solved by applying the documented fix.",
                "Generated summary",
                List.of("spring security"),
                List.of("cors"),
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
                "5".repeat(64),
                distance);
    }
}
