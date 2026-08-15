package io.github.aayush10016.trail.retrieval.application.service;

import io.github.aayush10016.trail.identity.domain.model.AuthenticatedPrincipal;
import io.github.aayush10016.trail.retrieval.application.dto.RecommendationEmbeddingProvenance;
import io.github.aayush10016.trail.retrieval.application.dto.TopProblemRecommendation;
import io.github.aayush10016.trail.retrieval.application.dto.TopProblemRecommendationResult;
import io.github.aayush10016.trail.retrieval.application.query.FindTopProblemRecommendationQuery;
import io.github.aayush10016.trail.retrieval.domain.model.HybridSimilarityMatch;
import io.github.aayush10016.trail.retrieval.domain.model.HybridSimilarityPolicy;
import io.github.aayush10016.trail.retrieval.domain.model.ProblemRecommendationMode;
import io.github.aayush10016.trail.retrieval.domain.model.ProblemRecommendationStrength;
import io.github.aayush10016.trail.retrieval.domain.model.ProblemVectorCandidate;
import io.github.aayush10016.trail.retrieval.domain.model.RecommendationEvidenceReference;
import io.github.aayush10016.trail.retrieval.domain.model.SimilarityEvidenceCode;
import io.github.aayush10016.trail.retrieval.domain.model.SolvedProblemSearchResult;
import io.github.aayush10016.trail.retrieval.domain.port.RecommendationSuppressionRepository;
import io.github.aayush10016.trail.retrieval.domain.service.HybridSimilarityRanker;
import io.github.aayush10016.trail.retrieval.domain.service.WhyThisExplanationBuilder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.transaction.annotation.Transactional;

/**
 * Application service that selects at most one recommendation for the current candidate Problem Session.
 */
public class TopProblemRecommendationService {

    private final SolvedProblemLexicalSearchService lexicalSearchService;
    private final ProblemVectorCandidateSearchService vectorCandidateSearchService;
    private final RecommendationSuppressionRepository suppressionRepository;
    private final HybridSimilarityRanker ranker;
    private final WhyThisExplanationBuilder explanationBuilder;

    /**
     * Creates the top recommendation service.
     */
    public TopProblemRecommendationService(
            SolvedProblemLexicalSearchService lexicalSearchService,
            ProblemVectorCandidateSearchService vectorCandidateSearchService,
            RecommendationSuppressionRepository suppressionRepository,
            HybridSimilarityRanker ranker,
            WhyThisExplanationBuilder explanationBuilder) {
        this.lexicalSearchService = Objects.requireNonNull(lexicalSearchService, "lexicalSearchService");
        this.vectorCandidateSearchService =
                Objects.requireNonNull(vectorCandidateSearchService, "vectorCandidateSearchService");
        this.suppressionRepository = Objects.requireNonNull(suppressionRepository, "suppressionRepository");
        this.ranker = Objects.requireNonNull(ranker, "ranker");
        this.explanationBuilder = Objects.requireNonNull(explanationBuilder, "explanationBuilder");
    }

    /**
     * Finds the single top recommendation card, or no match.
     */
    @Transactional(readOnly = true)
    public TopProblemRecommendationResult findTop(
            AuthenticatedPrincipal principal, FindTopProblemRecommendationQuery query) {
        Objects.requireNonNull(principal, "principal");
        Objects.requireNonNull(query, "query");

        var lexicalResults = lexicalSearchService.search(
                principal,
                query.queryText(),
                query.technologies(),
                query.errorPhrases(),
                query.candidateLimit());
        var vectorCandidates = vectorCandidateSearchService.findCandidates(
                principal,
                query.embeddingGenerationKey(),
                query.embeddingVector(),
                query.candidateLimit(),
                query.vectorQueryTimeout());
        var lexicalById = toLexicalById(lexicalResults);
        var vectorById = toVectorById(vectorCandidates);

        var strong = firstAllowed(
                principal,
                query,
                ranker.rank(lexicalResults, vectorCandidates, HybridSimilarityPolicy.conservativeV1()),
                lexicalById,
                vectorById,
                ProblemRecommendationStrength.STRONG);
        if (strong.isPresent() || query.mode() == ProblemRecommendationMode.PROACTIVE) {
            return strong.map(TopProblemRecommendationResult::found).orElseGet(TopProblemRecommendationResult::noMatch);
        }

        return firstAllowed(
                        principal,
                        query,
                        ranker.rank(lexicalResults, vectorCandidates, HybridSimilarityPolicy.possibleRecallV1()),
                        lexicalById,
                        vectorById,
                        ProblemRecommendationStrength.POSSIBLE)
                .map(TopProblemRecommendationResult::found)
                .orElseGet(TopProblemRecommendationResult::noMatch);
    }

    private Optional<TopProblemRecommendation> firstAllowed(
            AuthenticatedPrincipal principal,
            FindTopProblemRecommendationQuery query,
            List<HybridSimilarityMatch> matches,
            Map<UUID, SolvedProblemSearchResult> lexicalById,
            Map<UUID, ProblemVectorCandidate> vectorById,
            ProblemRecommendationStrength strength) {
        return matches.stream()
                .filter(match -> !match.problemSessionId().equals(query.currentProblemSessionId()))
                .filter(match -> !suppressionRepository.isSuppressed(
                        principal.userId(), query.currentProblemSessionId(), match.problemSessionId()))
                .findFirst()
                .map(match -> toRecommendation(query.currentProblemSessionId(), match, lexicalById, vectorById, strength));
    }

    private TopProblemRecommendation toRecommendation(
            UUID currentProblemSessionId,
            HybridSimilarityMatch match,
            Map<UUID, SolvedProblemSearchResult> lexicalById,
            Map<UUID, ProblemVectorCandidate> vectorById,
            ProblemRecommendationStrength strength) {
        var lexical = Objects.requireNonNull(lexicalById.get(match.problemSessionId()), "lexical result");
        var vector = Objects.requireNonNull(vectorById.get(match.problemSessionId()), "vector candidate");
        var explanation = explanationBuilder.build(
                match.problemSessionId(),
                match.algorithmVersion(),
                match.evidenceCodes().stream()
                        .map(code -> RecommendationEvidenceReference.session(match.problemSessionId(), code))
                        .toList());
        return new TopProblemRecommendation(
                stableRecommendationId(currentProblemSessionId, match.problemSessionId(), match.algorithmVersion()),
                currentProblemSessionId,
                match.problemSessionId(),
                strength,
                lexical.title(),
                lexical.finalSolution(),
                lexical.solvedAt(),
                lexical.durationSeconds(),
                match.algorithmVersion(),
                new RecommendationEmbeddingProvenance(
                        vector.generationKey(),
                        vector.generationVersion(),
                        vector.provider(),
                        vector.model(),
                        vector.inputFingerprint()),
                explanation);
    }

    private static UUID stableRecommendationId(UUID currentProblemSessionId, UUID recommendedProblemSessionId, String algorithmVersion) {
        return UUID.nameUUIDFromBytes(
                (currentProblemSessionId + ":" + recommendedProblemSessionId + ":" + algorithmVersion)
                        .getBytes(StandardCharsets.UTF_8));
    }

    private static Map<UUID, SolvedProblemSearchResult> toLexicalById(List<SolvedProblemSearchResult> results) {
        return results.stream()
                .collect(Collectors.toMap(
                        SolvedProblemSearchResult::problemSessionId,
                        Function.identity(),
                        (left, right) -> left,
                        LinkedHashMap::new));
    }

    private static Map<UUID, ProblemVectorCandidate> toVectorById(List<ProblemVectorCandidate> candidates) {
        return candidates.stream()
                .collect(Collectors.toMap(
                        ProblemVectorCandidate::problemSessionId,
                        Function.identity(),
                        (left, right) -> left,
                        LinkedHashMap::new));
    }
}
