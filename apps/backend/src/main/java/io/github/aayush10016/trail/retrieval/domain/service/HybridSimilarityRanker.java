package io.github.aayush10016.trail.retrieval.domain.service;

import io.github.aayush10016.trail.retrieval.domain.model.HybridSimilarityMatch;
import io.github.aayush10016.trail.retrieval.domain.model.HybridSimilarityPolicy;
import io.github.aayush10016.trail.retrieval.domain.model.ProblemVectorCandidate;
import io.github.aayush10016.trail.retrieval.domain.model.SearchEvidenceCode;
import io.github.aayush10016.trail.retrieval.domain.model.SimilarityEvidenceCode;
import io.github.aayush10016.trail.retrieval.domain.model.SolvedProblemSearchResult;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Deterministic hybrid ranker for solved Problem Session similarity.
 */
public class HybridSimilarityRanker {

    private static final double LEXICAL_SCORE_NORMALIZATION_CAP = 20.0;

    /**
     * Ranks vector candidates that are supported by non-technology lexical evidence.
     */
    public List<HybridSimilarityMatch> rank(
            List<SolvedProblemSearchResult> lexicalResults,
            List<ProblemVectorCandidate> vectorCandidates,
            HybridSimilarityPolicy policy) {
        Objects.requireNonNull(lexicalResults, "lexicalResults");
        Objects.requireNonNull(vectorCandidates, "vectorCandidates");
        Objects.requireNonNull(policy, "policy");

        var lexicalBySessionId = lexicalResults.stream()
                .collect(Collectors.toMap(
                        SolvedProblemSearchResult::problemSessionId,
                        Function.identity(),
                        HybridSimilarityRanker::higherScoredResult,
                        LinkedHashMap::new));

        return vectorCandidates.stream()
                .map(candidate -> toMatch(candidate, lexicalBySessionId.get(candidate.problemSessionId()), policy))
                .flatMap(java.util.Optional::stream)
                .sorted(Comparator.comparingDouble(HybridSimilarityMatch::hybridScore)
                        .reversed()
                        .thenComparing(HybridSimilarityMatch::vectorDistance)
                        .thenComparing(match -> match.problemSessionId().toString()))
                .toList();
    }

    private static java.util.Optional<HybridSimilarityMatch> toMatch(
            ProblemVectorCandidate vectorCandidate,
            SolvedProblemSearchResult lexicalResult,
            HybridSimilarityPolicy policy) {
        if (lexicalResult == null || vectorCandidate.distance() > policy.maximumVectorDistance()) {
            return java.util.Optional.empty();
        }

        var evidenceCodes = evidenceCodes(vectorCandidate, lexicalResult);
        if (!HybridSimilarityMatch.hasSupportingEvidence(evidenceCodes)) {
            return java.util.Optional.empty();
        }

        var hybridScore = hybridScore(vectorCandidate.distance(), lexicalResult.score(), policy);
        if (hybridScore < policy.minimumHybridScore()) {
            return java.util.Optional.empty();
        }

        return java.util.Optional.of(new HybridSimilarityMatch(
                vectorCandidate.problemSessionId(),
                hybridScore,
                vectorCandidate.distance(),
                lexicalResult.score(),
                evidenceCodes,
                policy.algorithmVersion()));
    }

    private static List<SimilarityEvidenceCode> evidenceCodes(
            ProblemVectorCandidate vectorCandidate, SolvedProblemSearchResult lexicalResult) {
        Objects.requireNonNull(vectorCandidate, "vectorCandidate");
        var evidence = new ArrayList<SimilarityEvidenceCode>();
        evidence.add(SimilarityEvidenceCode.VECTOR_DISTANCE);
        for (var code : lexicalResult.evidenceCodes()) {
            evidence.add(toSimilarityEvidence(code));
        }
        return List.copyOf(evidence);
    }

    private static SimilarityEvidenceCode toSimilarityEvidence(SearchEvidenceCode code) {
        return switch (code) {
            case LEXICAL_TEXT -> SimilarityEvidenceCode.LEXICAL_TEXT;
            case ERROR_PHRASE -> SimilarityEvidenceCode.ERROR_PHRASE;
            case TECHNOLOGY -> SimilarityEvidenceCode.TECHNOLOGY;
        };
    }

    private static double hybridScore(double vectorDistance, double lexicalScore, HybridSimilarityPolicy policy) {
        var normalizedVectorScore = Math.max(0.0, 1.0 - vectorDistance);
        var normalizedLexicalScore = Math.min(1.0, lexicalScore / LEXICAL_SCORE_NORMALIZATION_CAP);
        var totalWeight = policy.vectorWeight() + policy.lexicalWeight();
        return ((normalizedVectorScore * policy.vectorWeight()) + (normalizedLexicalScore * policy.lexicalWeight()))
                / totalWeight;
    }

    private static SolvedProblemSearchResult higherScoredResult(
            SolvedProblemSearchResult left, SolvedProblemSearchResult right) {
        return left.score() >= right.score() ? left : right;
    }
}
