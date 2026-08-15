package io.github.aayush10016.trail.retrieval.application.service;

import io.github.aayush10016.trail.identity.domain.model.AuthenticatedPrincipal;
import io.github.aayush10016.trail.retrieval.application.dto.RecommendationEventResponse;
import io.github.aayush10016.trail.retrieval.application.dto.TopProblemRecommendation;
import io.github.aayush10016.trail.retrieval.domain.model.RecommendationEvent;
import io.github.aayush10016.trail.retrieval.domain.model.RecommendationEvidenceReference;
import io.github.aayush10016.trail.retrieval.domain.model.RecommendationEventOutcome;
import io.github.aayush10016.trail.retrieval.domain.model.RecommendationEventProvenance;
import io.github.aayush10016.trail.retrieval.domain.port.RecommendationEventRepository;
import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.transaction.annotation.Transactional;

/**
 * Application service for server-derived and client-submitted recommendation lifecycle events.
 */
public class RecommendationEventService {

    private final RecommendationEventRepository eventRepository;
    private final Clock clock;
    private final Supplier<UUID> idGenerator;

    /**
     * Creates the event service.
     */
    public RecommendationEventService(
            RecommendationEventRepository eventRepository, Clock clock, Supplier<UUID> idGenerator) {
        this.eventRepository = Objects.requireNonNull(eventRepository, "eventRepository");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.idGenerator = Objects.requireNonNull(idGenerator, "idGenerator");
    }

    /**
     * Records the server-derived shown event for a recommendation, idempotently.
     */
    @Transactional
    public void recordShown(AuthenticatedPrincipal principal, TopProblemRecommendation recommendation) {
        Objects.requireNonNull(principal, "principal");
        Objects.requireNonNull(recommendation, "recommendation");
        eventRepository.insertIfAbsent(eventFor(
                principal,
                recommendation,
                RecommendationEventOutcome.SHOWN,
                recommendation.recommendationId(),
                recommendation.currentProblemSessionId(),
                recommendation.recommendedProblemSessionId()));
    }

    /**
     * Records one client-submitted outcome after a server-owned shown event exists.
     */
    @Transactional
    public RecommendationEventResponse recordClientOutcome(
            AuthenticatedPrincipal principal, UUID recommendationId, RecommendationEventOutcome outcome) {
        Objects.requireNonNull(principal, "principal");
        Objects.requireNonNull(recommendationId, "recommendationId");
        Objects.requireNonNull(outcome, "outcome");
        if (!outcome.clientSubmittable()) {
            throw new IllegalArgumentException("outcome is not client-submittable");
        }

        var shown = eventRepository
                .findShown(principal.userId(), recommendationId)
                .orElseThrow(ProblemRecommendationNotFoundException::new);
        var result = eventRepository.insertIfAbsent(new RecommendationEvent(
                idGenerator.get(),
                shown.userId(),
                shown.recommendationId(),
                shown.currentProblemSessionId(),
                shown.recommendedProblemSessionId(),
                outcome,
                shown.retrievedProblemSessionIds(),
                shown.evidence(),
                shown.embeddingProvenance(),
                shown.algorithmVersion(),
                clock.instant()));
        return new RecommendationEventResponse(
                recommendationId,
                outcome,
                result == RecommendationEventRepository.RecommendationEventWriteResult.INSERTED
                        ? RecommendationEventResponse.RecommendationEventStatus.ACCEPTED
                        : RecommendationEventResponse.RecommendationEventStatus.DUPLICATE);
    }

    private RecommendationEvent eventFor(
            AuthenticatedPrincipal principal,
            TopProblemRecommendation recommendation,
            RecommendationEventOutcome outcome,
            UUID recommendationId,
            UUID currentProblemSessionId,
            UUID recommendedProblemSessionId) {
        var provenance = recommendation.embeddingProvenance();
        return new RecommendationEvent(
                idGenerator.get(),
                principal.userId(),
                recommendationId,
                currentProblemSessionId,
                recommendedProblemSessionId,
                outcome,
                List.of(recommendedProblemSessionId),
                recommendation.explanation().messages().stream()
                        .map(message -> message.artifactId()
                                .map(artifactId -> RecommendationEvidenceReference.artifact(
                                                message.problemSessionId(), artifactId, message.evidenceCode()))
                                .orElseGet(() -> RecommendationEvidenceReference.session(
                                                message.problemSessionId(), message.evidenceCode())))
                        .toList(),
                new RecommendationEventProvenance(
                        provenance.generationKey(),
                        provenance.generationVersion(),
                        provenance.provider(),
                        provenance.model(),
                        provenance.inputFingerprint()),
                recommendation.algorithmVersion(),
                clock.instant());
    }
}
