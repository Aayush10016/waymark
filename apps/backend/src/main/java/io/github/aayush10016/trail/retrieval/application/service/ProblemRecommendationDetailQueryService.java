package io.github.aayush10016.trail.retrieval.application.service;

import io.github.aayush10016.trail.identity.domain.model.AuthenticatedPrincipal;
import io.github.aayush10016.trail.problem.domain.model.ProblemSessionStatus;
import io.github.aayush10016.trail.problem.domain.port.ProblemSessionRepository;
import io.github.aayush10016.trail.retrieval.application.dto.ProblemRecommendationDetailResponse;
import io.github.aayush10016.trail.retrieval.application.query.FindTopProblemRecommendationQuery;
import io.github.aayush10016.trail.retrieval.domain.model.ProblemRecommendationMode;
import io.github.aayush10016.trail.retrieval.domain.port.CurrentProblemRecommendationInputRepository;
import io.github.aayush10016.trail.shared.featureflag.FeatureFlag;
import io.github.aayush10016.trail.shared.featureflag.FeatureFlagService;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;

/**
 * Application query service for one current Problem Session recommendation detail.
 */
public class ProblemRecommendationDetailQueryService {

    private static final int CANDIDATE_LIMIT = 10;

    private final ProblemSessionRepository problemSessionRepository;
    private final CurrentProblemRecommendationInputRepository inputRepository;
    private final RecommendationEventService eventService;
    private final TopProblemRecommendationService topRecommendationService;
    private final FeatureFlagService featureFlagService;
    private final Duration vectorQueryTimeout;

    /**
     * Creates the recommendation detail query service.
     */
    public ProblemRecommendationDetailQueryService(
            ProblemSessionRepository problemSessionRepository,
            CurrentProblemRecommendationInputRepository inputRepository,
            RecommendationEventService eventService,
            TopProblemRecommendationService topRecommendationService,
            FeatureFlagService featureFlagService,
            Duration vectorQueryTimeout) {
        this.problemSessionRepository = Objects.requireNonNull(problemSessionRepository, "problemSessionRepository");
        this.inputRepository = Objects.requireNonNull(inputRepository, "inputRepository");
        this.eventService = Objects.requireNonNull(eventService, "eventService");
        this.topRecommendationService = Objects.requireNonNull(topRecommendationService, "topRecommendationService");
        this.featureFlagService = Objects.requireNonNull(featureFlagService, "featureFlagService");
        this.vectorQueryTimeout = requirePositive(vectorQueryTimeout);
    }

    /**
     * Returns a bounded recommendation detail or empty when no eligible recommendation exists.
     */
    @Transactional
    public Optional<ProblemRecommendationDetailResponse> findRecommendation(
            AuthenticatedPrincipal principal, UUID currentProblemSessionId) {
        Objects.requireNonNull(principal, "principal");
        Objects.requireNonNull(currentProblemSessionId, "currentProblemSessionId");

        var current = problemSessionRepository
                .findById(principal, currentProblemSessionId)
                .orElseThrow(ProblemRecommendationNotFoundException::new);
        if (current.deletedAt() != null) {
            throw new ProblemRecommendationNotFoundException();
        }
        if (current.session().status() != ProblemSessionStatus.CANDIDATE
                && current.session().status() != ProblemSessionStatus.REVIEWABLE) {
            return Optional.empty();
        }
        if (!recommendationsEnabled()) {
            return Optional.empty();
        }

        return inputRepository
                .findInput(principal.userId(), currentProblemSessionId)
                .flatMap(input -> topRecommendationService
                        .findTop(
                                principal,
                                new FindTopProblemRecommendationQuery(
                                        input.problemSessionId(),
                                        input.queryText(),
                                        input.technologies(),
                                        input.errorPhrases(),
                                        input.embeddingGenerationKey(),
                                        input.embeddingVector(),
                                        CANDIDATE_LIMIT,
                                        vectorQueryTimeout,
                                        ProblemRecommendationMode.PROACTIVE))
                        .recommendation())
                .map(recommendation -> {
                    eventService.recordShown(principal, recommendation);
                    return ProblemRecommendationDetailResponse.from(recommendation);
                });
    }

    private boolean recommendationsEnabled() {
        return featureFlagService.isEnabled(FeatureFlag.AI_SUGGESTIONS)
                && featureFlagService.isEnabled(FeatureFlag.GHOST_REPLAY)
                && featureFlagService.isEnabled(FeatureFlag.SEMANTIC_SEARCH);
    }

    private static Duration requirePositive(Duration duration) {
        Objects.requireNonNull(duration, "duration");
        if (duration.isZero() || duration.isNegative()) {
            throw new IllegalArgumentException("vectorQueryTimeout must be positive");
        }
        return duration;
    }
}
