package io.github.aayush10016.trail.retrieval.config;

import io.github.aayush10016.trail.problem.domain.port.ProblemSessionRepository;
import io.github.aayush10016.trail.retrieval.application.service.ProblemRecommendationDetailQueryService;
import io.github.aayush10016.trail.retrieval.application.service.ProblemVectorCandidateSearchService;
import io.github.aayush10016.trail.retrieval.application.service.RecommendationEventService;
import io.github.aayush10016.trail.retrieval.application.service.SolvedProblemLexicalSearchService;
import io.github.aayush10016.trail.retrieval.application.service.TopProblemRecommendationService;
import io.github.aayush10016.trail.retrieval.domain.port.CurrentProblemRecommendationInputRepository;
import io.github.aayush10016.trail.retrieval.domain.port.ProblemVectorCandidateRepository;
import io.github.aayush10016.trail.retrieval.domain.port.RecommendationEventRepository;
import io.github.aayush10016.trail.retrieval.domain.port.RecommendationSuppressionRepository;
import io.github.aayush10016.trail.retrieval.domain.port.SolvedProblemLexicalSearchRepository;
import io.github.aayush10016.trail.retrieval.domain.service.HybridSimilarityRanker;
import io.github.aayush10016.trail.retrieval.domain.service.WhyThisExplanationBuilder;
import io.github.aayush10016.trail.shared.featureflag.FeatureFlagService;
import java.time.Clock;
import java.time.Duration;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires retrieval capability services.
 */
@Configuration
public class RetrievalConfiguration {

    /**
     * Creates solved Problem Session lexical search service.
     */
    @Bean
    public SolvedProblemLexicalSearchService solvedProblemLexicalSearchService(
            SolvedProblemLexicalSearchRepository repository) {
        return new SolvedProblemLexicalSearchService(repository);
    }

    /**
     * Creates the vector candidate search application service.
     */
    @Bean
    public ProblemVectorCandidateSearchService problemVectorCandidateSearchService(
            ProblemVectorCandidateRepository repository) {
        return new ProblemVectorCandidateSearchService(repository);
    }

    /**
     * Creates the top recommendation lifecycle query service.
     */
    @Bean
    public TopProblemRecommendationService topProblemRecommendationService(
            SolvedProblemLexicalSearchService lexicalSearchService,
            ProblemVectorCandidateSearchService vectorCandidateSearchService,
            RecommendationSuppressionRepository suppressionRepository) {
        return new TopProblemRecommendationService(
                lexicalSearchService,
                vectorCandidateSearchService,
                suppressionRepository,
                new HybridSimilarityRanker(),
                new WhyThisExplanationBuilder());
    }

    /**
     * Creates recommendation lifecycle event service.
     */
    @Bean
    public RecommendationEventService recommendationEventService(RecommendationEventRepository repository, Clock clock) {
        return new RecommendationEventService(repository, clock, UUID::randomUUID);
    }

    /**
     * Creates the recommendation detail API query service.
     */
    @Bean
    public ProblemRecommendationDetailQueryService problemRecommendationDetailQueryService(
            ProblemSessionRepository problemSessionRepository,
            CurrentProblemRecommendationInputRepository inputRepository,
            RecommendationEventService eventService,
            TopProblemRecommendationService topRecommendationService,
            FeatureFlagService featureFlagService,
            @Value("${trail.retrieval.vector-query.timeout:2s}") Duration vectorQueryTimeout) {
        return new ProblemRecommendationDetailQueryService(
                problemSessionRepository,
                inputRepository,
                eventService,
                topRecommendationService,
                featureFlagService,
                vectorQueryTimeout);
    }
}
