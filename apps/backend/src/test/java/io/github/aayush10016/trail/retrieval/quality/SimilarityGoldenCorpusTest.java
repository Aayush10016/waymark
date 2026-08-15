package io.github.aayush10016.trail.retrieval.quality;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.aayush10016.trail.retrieval.domain.model.HybridSimilarityMatch;
import io.github.aayush10016.trail.retrieval.domain.model.HybridSimilarityPolicy;
import io.github.aayush10016.trail.retrieval.domain.model.ProblemVectorCandidate;
import io.github.aayush10016.trail.retrieval.domain.model.SearchEvidenceCode;
import io.github.aayush10016.trail.retrieval.domain.model.SolvedProblemSearchResult;
import io.github.aayush10016.trail.retrieval.domain.service.HybridSimilarityRanker;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SimilarityGoldenCorpusTest {

    private static final String CORPUS_PATH = "/retrieval/similarity-golden-corpus-v1.json";
    private static final String FINGERPRINT = "7".repeat(64);

    private final HybridSimilarityRanker ranker = new HybridSimilarityRanker();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void corpusContainsRequiredCohortsAndDocumentedPrecisionGate() throws IOException {
        var corpus = corpus();

        assertThat(corpus.path("version").asText()).isEqualTo("similarity-golden-corpus-v1");
        assertThat(corpus.path("precisionGate").asDouble()).isGreaterThanOrEqualTo(0.90);
        assertThat(corpus.path("cohorts").path("positive")).isNotEmpty();
        assertThat(corpus.path("cohorts").path("sameTechnology")).isNotEmpty();
        assertThat(corpus.path("cohorts").path("sameErrorDifferentCause")).isNotEmpty();
        assertThat(corpus.path("cohorts").path("unrelated")).isNotEmpty();
    }

    @Test
    void hybridRankerMeetsGoldenCorpusPrecisionGate() throws IOException {
        var corpus = corpus();
        var cases = cases(corpus.path("cohorts"));
        var lexicalResults = cases.stream().map(GoldenCase::searchResult).toList();
        var vectorCandidates = cases.stream().map(GoldenCase::vectorCandidate).toList();

        var matches = ranker.rank(lexicalResults, vectorCandidates, HybridSimilarityPolicy.conservativeV1());
        var matchedIds = matches.stream().map(HybridSimilarityMatch::problemSessionId).toList();
        var expectedPositiveIds = cases.stream()
                .filter(GoldenCase::expectedMatch)
                .map(GoldenCase::id)
                .toList();
        var falsePositiveCount = cases.stream()
                .filter(goldenCase -> !goldenCase.expectedMatch())
                .filter(goldenCase -> matchedIds.contains(goldenCase.id()))
                .count();
        var precision = matches.isEmpty() ? 1.0 : (double) (matches.size() - falsePositiveCount) / matches.size();

        assertThat(matchedIds).containsExactlyElementsOf(expectedPositiveIds);
        assertThat(precision).isGreaterThanOrEqualTo(corpus.path("precisionGate").asDouble());
    }

    private JsonNode corpus() throws IOException {
        try (var inputStream = getClass().getResourceAsStream(CORPUS_PATH)) {
            assertThat(inputStream).as("golden corpus resource").isNotNull();
            return objectMapper.readTree(inputStream);
        }
    }

    private static List<GoldenCase> cases(JsonNode cohorts) {
        var cases = new ArrayList<GoldenCase>();
        cohorts.properties().forEach(entry -> entry.getValue().forEach(node -> cases.add(GoldenCase.from(node))));
        return List.copyOf(cases);
    }

    private record GoldenCase(
            UUID id,
            String title,
            String finalSolution,
            List<String> technologies,
            List<String> errorPhrases,
            double lexicalScore,
            double vectorDistance,
            List<SearchEvidenceCode> evidenceCodes,
            boolean expectedMatch) {

        private static GoldenCase from(JsonNode node) {
            return new GoldenCase(
                    UUID.fromString(node.path("id").asText()),
                    node.path("title").asText(),
                    node.path("finalSolution").asText(),
                    strings(node.path("technologies")),
                    strings(node.path("errorPhrases")),
                    node.path("lexicalScore").asDouble(),
                    node.path("vectorDistance").asDouble(),
                    evidenceCodes(node.path("evidenceCodes")),
                    node.path("expectedMatch").asBoolean());
        }

        private SolvedProblemSearchResult searchResult() {
            return new SolvedProblemSearchResult(
                    id,
                    title,
                    finalSolution,
                    "Golden corpus summary",
                    technologies,
                    errorPhrases,
                    "CONFIGURATION",
                    lexicalScore,
                    evidenceCodes,
                    Instant.parse("2026-07-18T10:00:00Z"));
        }

        private ProblemVectorCandidate vectorCandidate() {
            return new ProblemVectorCandidate(
                    id,
                    "solved-session",
                    1,
                    "mock",
                    "trail-mock-embedding",
                    FINGERPRINT,
                    vectorDistance);
        }

        private static List<String> strings(JsonNode node) {
            var values = new ArrayList<String>();
            node.forEach(value -> values.add(value.asText()));
            return List.copyOf(values);
        }

        private static List<SearchEvidenceCode> evidenceCodes(JsonNode node) {
            var values = new ArrayList<SearchEvidenceCode>();
            node.forEach(value -> values.add(SearchEvidenceCode.valueOf(value.asText())));
            return List.copyOf(values);
        }
    }
}
