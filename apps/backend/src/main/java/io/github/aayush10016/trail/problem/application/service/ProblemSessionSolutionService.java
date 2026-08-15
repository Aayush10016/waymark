package io.github.aayush10016.trail.problem.application.service;

import io.github.aayush10016.trail.backgroundjob.domain.model.BackgroundJob;
import io.github.aayush10016.trail.backgroundjob.domain.port.BackgroundJobRepository;
import io.github.aayush10016.trail.identity.domain.model.AuthenticatedPrincipal;
import io.github.aayush10016.trail.problem.application.command.SaveProblemSessionSolutionCommand;
import io.github.aayush10016.trail.problem.application.dto.ProblemSessionSolutionResponse;
import io.github.aayush10016.trail.problem.domain.model.FinalSolution;
import io.github.aayush10016.trail.problem.domain.model.ProblemSessionSnapshot;
import io.github.aayush10016.trail.problem.domain.model.ProblemSessionStatus;
import io.github.aayush10016.trail.problem.domain.model.ProblemSessionTransitionException;
import io.github.aayush10016.trail.problem.domain.port.ProblemSessionRepository;
import io.github.aayush10016.trail.problem.domain.port.SessionArtifactRepository;
import java.time.Clock;
import java.util.Objects;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;

/**
 * Application service for saving exact user-authored Problem Session solutions.
 */
public class ProblemSessionSolutionService {

    private final ProblemSessionRepository problemSessionRepository;
    private final SessionArtifactRepository sessionArtifactRepository;
    private final BackgroundJobRepository backgroundJobRepository;
    private final Clock clock;

    /**
     * Creates the service.
     */
    public ProblemSessionSolutionService(
            ProblemSessionRepository problemSessionRepository,
            SessionArtifactRepository sessionArtifactRepository,
            BackgroundJobRepository backgroundJobRepository,
            Clock clock) {
        this.problemSessionRepository = Objects.requireNonNull(problemSessionRepository, "problemSessionRepository");
        this.sessionArtifactRepository = Objects.requireNonNull(sessionArtifactRepository, "sessionArtifactRepository");
        this.backgroundJobRepository = Objects.requireNonNull(backgroundJobRepository, "backgroundJobRepository");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * Saves a final solution and records the required post-solution background job atomically.
     */
    @Transactional
    public ProblemSessionSolutionResponse save(
            AuthenticatedPrincipal principal, SaveProblemSessionSolutionCommand command) {
        Objects.requireNonNull(principal, "principal");
        Objects.requireNonNull(command, "command");

        validateIdempotencyKey(command.idempotencyKey());
        validateFinalSolutionText(command.finalSolutionText());
        var current = problemSessionRepository
                .findById(principal, command.problemSessionId())
                .orElseThrow(ProblemSessionNotFoundException::new);
        var solution = FinalSolution.userAuthored(command.finalSolutionText(), command.authoredAt());

        if (current.session().status() == ProblemSessionStatus.SOLVED) {
            return handleSolvedRetry(principal, command, current, solution);
        }
        if (current.session().status() != ProblemSessionStatus.REVIEWABLE) {
            throw ProblemSessionTransitionException.illegal(current.session().status(), ProblemSessionStatus.SOLVED);
        }
        if (command.expectedVersion() != current.version()) {
            throw new ProblemSessionVersionConflictException();
        }

        var solved = new ProblemSessionSnapshot(
                current.id(),
                current.source(),
                current.session().solve(solution),
                current.version(),
                current.title(),
                current.description(),
                current.localNote(),
                current.startedAt(),
                current.endedAt(),
                current.createdAt(),
                command.authoredAt(),
                current.deletedAt());
        if (!problemSessionRepository.update(principal, solved, command.expectedVersion())) {
            throw new ProblemSessionVersionConflictException();
        }
        sessionArtifactRepository.promoteAssociatedCaptureEvents(principal, current.id());
        recordPostSolutionJobs(principal, current.id());
        return ProblemSessionSolutionResponse.from(problemSessionRepository
                .findById(principal, current.id())
                .orElseThrow(ProblemSessionNotFoundException::new));
    }

    private ProblemSessionSolutionResponse handleSolvedRetry(
            AuthenticatedPrincipal principal,
            SaveProblemSessionSolutionCommand command,
            ProblemSessionSnapshot current,
            FinalSolution submittedSolution) {
        var currentSolution = current.session().finalSolution().orElseThrow();
        if (currentSolution.text().equals(submittedSolution.text()) && command.expectedVersion() <= current.version()) {
            sessionArtifactRepository.promoteAssociatedCaptureEvents(principal, current.id());
            recordPostSolutionJobs(principal, current.id());
            return ProblemSessionSolutionResponse.from(current);
        }
        if (command.expectedVersion() != current.version()) {
            throw new ProblemSessionVersionConflictException();
        }
        var updated = new ProblemSessionSnapshot(
                current.id(),
                current.source(),
                current.session().updateSolution(submittedSolution),
                current.version(),
                current.title(),
                current.description(),
                current.localNote(),
                current.startedAt(),
                current.endedAt(),
                current.createdAt(),
                command.authoredAt(),
                current.deletedAt());
        if (!problemSessionRepository.update(principal, updated, command.expectedVersion())) {
            throw new ProblemSessionVersionConflictException();
        }
        sessionArtifactRepository.promoteAssociatedCaptureEvents(principal, current.id());
        recordPostSolutionJobs(principal, current.id());
        return ProblemSessionSolutionResponse.from(problemSessionRepository
                .findById(principal, current.id())
                .orElseThrow(ProblemSessionNotFoundException::new));
    }

    private void recordPostSolutionJobs(AuthenticatedPrincipal principal, UUID problemSessionId) {
        var now = clock.instant();
        backgroundJobRepository.insertIfAbsent(
                BackgroundJob.problemSessionEnrichment(UUID.randomUUID(), principal.userId(), problemSessionId, now));
        backgroundJobRepository.insertIfAbsent(
                BackgroundJob.solvedProblemSessionEmbedding(UUID.randomUUID(), principal.userId(), problemSessionId, now));
    }

    private static void validateIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey.isBlank() || idempotencyKey.length() > 128) {
            throw new InvalidProblemSessionCommandException("Idempotency-Key is invalid.");
        }
    }

    private static void validateFinalSolutionText(String finalSolutionText) {
        if (finalSolutionText.isBlank()) {
            throw new InvalidProblemSessionCommandException("Final solution must not be blank.");
        }
    }
}
