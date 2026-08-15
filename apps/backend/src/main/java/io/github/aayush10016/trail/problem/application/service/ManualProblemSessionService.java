package io.github.aayush10016.trail.problem.application.service;

import io.github.aayush10016.trail.identity.domain.model.AuthenticatedPrincipal;
import io.github.aayush10016.trail.problem.application.command.FinishProblemSessionCommand;
import io.github.aayush10016.trail.problem.application.command.StartProblemSessionCommand;
import io.github.aayush10016.trail.problem.application.dto.ProblemSessionStateResponse;
import io.github.aayush10016.trail.problem.domain.model.ProblemSession;
import io.github.aayush10016.trail.problem.domain.model.ProblemSessionSnapshot;
import io.github.aayush10016.trail.problem.domain.model.ProblemSessionSource;
import io.github.aayush10016.trail.problem.domain.model.ProblemSessionStatus;
import io.github.aayush10016.trail.problem.domain.model.ProblemSessionTransitionException;
import io.github.aayush10016.trail.problem.domain.port.ProblemSessionRepository;
import java.time.Clock;
import java.util.Objects;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;

/**
 * Application service for manual Problem Session start and finish commands.
 */
public class ManualProblemSessionService {

    private static final int MAXIMUM_TITLE_LENGTH = 200;

    private final ProblemSessionRepository problemSessionRepository;
    private final Clock clock;

    /**
     * Creates the service.
     */
    public ManualProblemSessionService(ProblemSessionRepository problemSessionRepository, Clock clock) {
        this.problemSessionRepository = Objects.requireNonNull(problemSessionRepository, "problemSessionRepository");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * Manually starts a candidate Problem Session for the authenticated user.
     */
    @Transactional
    public ProblemSessionStateResponse start(AuthenticatedPrincipal principal, StartProblemSessionCommand command) {
        Objects.requireNonNull(principal, "principal");
        Objects.requireNonNull(command, "command");

        var problemSessionId = command.problemSessionId() == null ? UUID.randomUUID() : command.problemSessionId();
        var existing = problemSessionRepository.findById(principal, problemSessionId);
        if (existing.isPresent()) {
            return ProblemSessionStateResponse.from(existing.get());
        }

        var now = command.startedAt() == null ? clock.instant() : command.startedAt();
        var snapshot = new ProblemSessionSnapshot(
                problemSessionId,
                ProblemSessionSource.MANUAL,
                ProblemSession.candidate(),
                0L,
                normalizeTitle(command.title()),
                null,
                null,
                now,
                null,
                now,
                now,
                null);
        problemSessionRepository.insert(principal, snapshot);
        return ProblemSessionStateResponse.from(snapshot);
    }

    /**
     * Manually finishes a candidate Problem Session capture boundary.
     */
    @Transactional
    public ProblemSessionStateResponse finish(AuthenticatedPrincipal principal, FinishProblemSessionCommand command) {
        Objects.requireNonNull(principal, "principal");
        Objects.requireNonNull(command, "command");

        var snapshot = problemSessionRepository
                .findById(principal, command.problemSessionId())
                .orElseThrow(ProblemSessionNotFoundException::new);

        if (snapshot.session().status() == ProblemSessionStatus.REVIEWABLE) {
            if (command.expectedVersion() > snapshot.version()) {
                throw new ProblemSessionVersionConflictException();
            }
            return ProblemSessionStateResponse.from(snapshot);
        }
        if (snapshot.session().status() != ProblemSessionStatus.CANDIDATE) {
            throw ProblemSessionTransitionException.illegal(snapshot.session().status(), ProblemSessionStatus.REVIEWABLE);
        }
        if (command.expectedVersion() != snapshot.version()) {
            throw new ProblemSessionVersionConflictException();
        }
        if (command.finishedAt().isBefore(snapshot.startedAt())) {
            throw new InvalidProblemSessionCommandException("Finish time must not be before start time.");
        }

        var updatedSnapshot = new ProblemSessionSnapshot(
                snapshot.id(),
                snapshot.source(),
                snapshot.session().markReviewable(),
                snapshot.version(),
                snapshot.title(),
                snapshot.description(),
                snapshot.localNote(),
                snapshot.startedAt(),
                command.finishedAt(),
                snapshot.createdAt(),
                command.finishedAt(),
                snapshot.deletedAt());
        if (!problemSessionRepository.update(principal, updatedSnapshot, command.expectedVersion())) {
            throw new ProblemSessionVersionConflictException();
        }
        return ProblemSessionStateResponse.from(problemSessionRepository
                .findById(principal, snapshot.id())
                .orElseThrow(ProblemSessionNotFoundException::new));
    }

    private static String normalizeTitle(String title) {
        if (title == null || title.isBlank()) {
            return null;
        }
        var normalized = title.strip();
        if (normalized.length() > MAXIMUM_TITLE_LENGTH) {
            throw new InvalidProblemSessionCommandException("Problem Session title is too long.");
        }
        return normalized;
    }
}
