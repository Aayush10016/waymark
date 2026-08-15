package io.github.aayush10016.trail.problem.application.service;

import io.github.aayush10016.trail.identity.domain.model.AuthenticatedPrincipal;
import io.github.aayush10016.trail.problem.domain.port.ProblemSessionDeletionRepository;
import java.time.Clock;
import java.util.Objects;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;

/**
 * Application service for privacy-preserving Problem Session deletion.
 */
public class ProblemSessionDeletionService {

    private final ProblemSessionDeletionRepository deletionRepository;
    private final Clock clock;

    /**
     * Creates the deletion service.
     */
    public ProblemSessionDeletionService(ProblemSessionDeletionRepository deletionRepository, Clock clock) {
        this.deletionRepository = Objects.requireNonNull(deletionRepository, "deletionRepository");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * Deletes one owned Problem Session and all active derived records.
     */
    @Transactional
    public void delete(AuthenticatedPrincipal principal, UUID problemSessionId) {
        Objects.requireNonNull(principal, "principal");
        Objects.requireNonNull(problemSessionId, "problemSessionId");

        if (!deletionRepository.delete(principal, problemSessionId, clock.instant())) {
            throw new ProblemSessionNotFoundException();
        }
    }
}
