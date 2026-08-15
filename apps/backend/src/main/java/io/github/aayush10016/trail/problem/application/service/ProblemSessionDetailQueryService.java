package io.github.aayush10016.trail.problem.application.service;

import io.github.aayush10016.trail.identity.domain.model.AuthenticatedPrincipal;
import io.github.aayush10016.trail.problem.application.dto.ProblemSessionDetailResponse;
import io.github.aayush10016.trail.problem.domain.port.ProblemSessionRepository;
import io.github.aayush10016.trail.problem.domain.port.SessionArtifactRepository;
import java.util.Objects;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;

/**
 * Application query service for user-owned Problem Session detail reads.
 */
public class ProblemSessionDetailQueryService {

    /**
     * Maximum active artifacts returned by the detail API.
     */
    public static final int MAXIMUM_RETURNED_ARTIFACTS = 100;

    private final ProblemSessionRepository problemSessionRepository;
    private final SessionArtifactRepository sessionArtifactRepository;

    /**
     * Creates the query service.
     */
    public ProblemSessionDetailQueryService(
            ProblemSessionRepository problemSessionRepository, SessionArtifactRepository sessionArtifactRepository) {
        this.problemSessionRepository = Objects.requireNonNull(problemSessionRepository, "problemSessionRepository");
        this.sessionArtifactRepository = Objects.requireNonNull(sessionArtifactRepository, "sessionArtifactRepository");
    }

    /**
     * Returns a bounded detail response for a Problem Session owned by the authenticated user.
     */
    @Transactional(readOnly = true)
    public ProblemSessionDetailResponse getDetail(AuthenticatedPrincipal principal, UUID problemSessionId) {
        Objects.requireNonNull(principal, "principal");
        Objects.requireNonNull(problemSessionId, "problemSessionId");

        var snapshot = problemSessionRepository
                .findById(principal, problemSessionId)
                .orElseThrow(ProblemSessionNotFoundException::new);
        var activeArtifacts = sessionArtifactRepository.findActiveByProblemSession(principal, problemSessionId);
        var boundedArtifacts = activeArtifacts.stream().limit(MAXIMUM_RETURNED_ARTIFACTS).toList();
        var omittedArtifactCount = activeArtifacts.size() - boundedArtifacts.size();
        return ProblemSessionDetailResponse.from(snapshot, boundedArtifacts, omittedArtifactCount);
    }
}
