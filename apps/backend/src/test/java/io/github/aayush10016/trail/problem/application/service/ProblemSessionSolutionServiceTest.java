package io.github.aayush10016.trail.problem.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.aayush10016.trail.backgroundjob.domain.model.BackgroundJob;
import io.github.aayush10016.trail.backgroundjob.domain.model.BackgroundJobType;
import io.github.aayush10016.trail.backgroundjob.domain.port.BackgroundJobRepository;
import io.github.aayush10016.trail.identity.domain.model.AuthenticatedPrincipal;
import io.github.aayush10016.trail.problem.application.command.SaveProblemSessionSolutionCommand;
import io.github.aayush10016.trail.problem.domain.model.ProblemSession;
import io.github.aayush10016.trail.problem.domain.model.ProblemSessionSnapshot;
import io.github.aayush10016.trail.problem.domain.model.ProblemSessionSource;
import io.github.aayush10016.trail.problem.domain.model.ProblemSessionStatus;
import io.github.aayush10016.trail.problem.domain.model.ProblemSessionTransitionException;
import io.github.aayush10016.trail.problem.domain.model.SessionArtifact;
import io.github.aayush10016.trail.problem.domain.port.ProblemSessionRepository;
import io.github.aayush10016.trail.problem.domain.port.SessionArtifactRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ProblemSessionSolutionServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-13T09:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final AuthenticatedPrincipal PRINCIPAL =
            new AuthenticatedPrincipal(UUID.fromString("10000000-0000-4000-8000-000000000001"));

    private final InMemoryProblemSessionRepository sessionRepository = new InMemoryProblemSessionRepository();
    private final InMemoryBackgroundJobRepository jobRepository = new InMemoryBackgroundJobRepository();
    private final InMemorySessionArtifactRepository artifactRepository = new InMemorySessionArtifactRepository();
    private final ProblemSessionSolutionService service =
            new ProblemSessionSolutionService(sessionRepository, artifactRepository, jobRepository, CLOCK);

    @Test
    void savesExactUserAuthoredSolutionAndCreatesPostSolutionJobs() {
        var problemSessionId = UUID.fromString("20000000-0000-4000-8000-000000000050");
        sessionRepository.insert(PRINCIPAL, reviewableSnapshot(problemSessionId, 1L));
        var exactSolution = "  Allowed localhost:3000 and moved the JWT filter before UsernamePasswordAuthenticationFilter.  ";

        var result = service.save(PRINCIPAL, command(problemSessionId, 1L, "solution-key-1", exactSolution));

        assertThat(result.status()).isEqualTo("SOLVED");
        assertThat(result.version()).isEqualTo(2L);
        assertThat(result.finalSolution()).isEqualTo(exactSolution);
        assertThat(result.finalSolutionAuthor()).isEqualTo("USER");
        assertThat(result.solutionAuthoredAt()).isEqualTo(NOW);
        assertThat(jobRepository.insertedCount()).isEqualTo(2);
        assertThat(artifactRepository.promoted()).containsExactly(problemSessionId);
    }

    @Test
    void exactRetryIsIdempotentAndDoesNotCreateDuplicateJob() {
        var problemSessionId = UUID.fromString("20000000-0000-4000-8000-000000000051");
        var exactSolution = "Configured CORS and JWT filter ordering.";
        sessionRepository.insert(PRINCIPAL, reviewableSnapshot(problemSessionId, 1L));

        var first = service.save(PRINCIPAL, command(problemSessionId, 1L, "solution-key-2", exactSolution));
        var retry = service.save(PRINCIPAL, command(problemSessionId, 1L, "solution-key-2", exactSolution));

        assertThat(retry).isEqualTo(first);
        assertThat(sessionRepository.findById(PRINCIPAL, problemSessionId).orElseThrow().version()).isEqualTo(2L);
        assertThat(jobRepository.insertedCount()).isEqualTo(2);
        assertThat(artifactRepository.promoted()).containsExactly(problemSessionId, problemSessionId);
    }

    @Test
    void rejectsMismatchedSolvedRetry() {
        var problemSessionId = UUID.fromString("20000000-0000-4000-8000-000000000052");
        sessionRepository.insert(PRINCIPAL, reviewableSnapshot(problemSessionId, 1L));
        service.save(PRINCIPAL, command(problemSessionId, 1L, "solution-key-3", "Original solution."));

        assertThatThrownBy(() -> service.save(
                        PRINCIPAL, command(problemSessionId, 1L, "solution-key-3", "Different solution.")))
                .isInstanceOf(ProblemSessionVersionConflictException.class);
    }

    @Test
    void updatesSolvedSolutionWithCurrentVersionAndDoesNotDuplicateJob() {
        var problemSessionId = UUID.fromString("20000000-0000-4000-8000-000000000057");
        sessionRepository.insert(PRINCIPAL, reviewableSnapshot(problemSessionId, 1L));
        service.save(PRINCIPAL, command(problemSessionId, 1L, "solution-key-8", "Original solution."));

        var updated = service.save(PRINCIPAL, command(problemSessionId, 2L, "solution-key-9", "Revised solution."));

        assertThat(updated.version()).isEqualTo(3L);
        assertThat(updated.finalSolution()).isEqualTo("Revised solution.");
        assertThat(jobRepository.insertedCount()).isEqualTo(2);
        assertThat(artifactRepository.promoted()).containsExactly(problemSessionId, problemSessionId);
    }

    @Test
    void rejectsStaleVersionBeforeSolving() {
        var problemSessionId = UUID.fromString("20000000-0000-4000-8000-000000000053");
        sessionRepository.insert(PRINCIPAL, reviewableSnapshot(problemSessionId, 1L));

        assertThatThrownBy(() -> service.save(PRINCIPAL, command(problemSessionId, 0L, "solution-key-4", "Solved.")))
                .isInstanceOf(ProblemSessionVersionConflictException.class);
        assertThat(jobRepository.insertedCount()).isZero();
    }

    @Test
    void rejectsIllegalStateTransition() {
        var problemSessionId = UUID.fromString("20000000-0000-4000-8000-000000000054");
        sessionRepository.insert(PRINCIPAL, candidateSnapshot(problemSessionId, 0L));

        assertThatThrownBy(() -> service.save(PRINCIPAL, command(problemSessionId, 0L, "solution-key-5", "Solved.")))
                .isInstanceOf(ProblemSessionTransitionException.class);
        assertThat(jobRepository.insertedCount()).isZero();
    }

    @Test
    void crossUserAccessIsNotFoundAndCreatesNoJob() {
        var problemSessionId = UUID.fromString("20000000-0000-4000-8000-000000000055");
        var otherPrincipal = new AuthenticatedPrincipal(UUID.fromString("10000000-0000-4000-8000-000000000002"));
        sessionRepository.insert(PRINCIPAL, reviewableSnapshot(problemSessionId, 1L));

        assertThatThrownBy(() -> service.save(otherPrincipal, command(problemSessionId, 1L, "solution-key-6", "Solved.")))
                .isInstanceOf(ProblemSessionNotFoundException.class);
        assertThat(jobRepository.insertedCount()).isZero();
    }

    @Test
    void rejectsBlankIdempotencyKeyAndBlankSolution() {
        var problemSessionId = UUID.fromString("20000000-0000-4000-8000-000000000056");
        sessionRepository.insert(PRINCIPAL, reviewableSnapshot(problemSessionId, 1L));

        assertThatThrownBy(() -> service.save(PRINCIPAL, command(problemSessionId, 1L, " ", "Solved.")))
                .isInstanceOf(InvalidProblemSessionCommandException.class);
        assertThatThrownBy(() -> service.save(PRINCIPAL, command(problemSessionId, 1L, "solution-key-7", "   ")))
                .isInstanceOf(InvalidProblemSessionCommandException.class);
        assertThat(jobRepository.insertedCount()).isZero();
    }

    private static SaveProblemSessionSolutionCommand command(
            UUID problemSessionId, long expectedVersion, String idempotencyKey, String finalSolution) {
        return new SaveProblemSessionSolutionCommand(problemSessionId, expectedVersion, idempotencyKey, finalSolution, NOW);
    }

    private static ProblemSessionSnapshot candidateSnapshot(UUID id, long version) {
        return snapshot(id, ProblemSession.candidate(), version);
    }

    private static ProblemSessionSnapshot reviewableSnapshot(UUID id, long version) {
        return snapshot(id, ProblemSession.candidate().markReviewable(), version);
    }

    private static ProblemSessionSnapshot snapshot(UUID id, ProblemSession session, long version) {
        return new ProblemSessionSnapshot(
                id,
                ProblemSessionSource.MANUAL,
                session,
                version,
                "Spring CORS",
                null,
                null,
                Instant.parse("2026-07-13T08:00:00Z"),
                Instant.parse("2026-07-13T08:30:00Z"),
                Instant.parse("2026-07-13T08:00:00Z"),
                Instant.parse("2026-07-13T08:30:00Z"),
                null);
    }

    private static final class InMemoryProblemSessionRepository implements ProblemSessionRepository {

        private final Map<String, ProblemSessionSnapshot> snapshots = new HashMap<>();

        @Override
        public void insert(AuthenticatedPrincipal principal, ProblemSessionSnapshot snapshot) {
            snapshots.put(key(principal, snapshot.id()), snapshot);
        }

        @Override
        public Optional<ProblemSessionSnapshot> findById(AuthenticatedPrincipal principal, UUID problemSessionId) {
            return Optional.ofNullable(snapshots.get(key(principal, problemSessionId)));
        }

        @Override
        public boolean update(AuthenticatedPrincipal principal, ProblemSessionSnapshot snapshot, long expectedVersion) {
            var key = key(principal, snapshot.id());
            var current = snapshots.get(key);
            if (current == null || current.version() != expectedVersion) {
                return false;
            }
            snapshots.put(
                    key,
                    new ProblemSessionSnapshot(
                            snapshot.id(),
                            snapshot.source(),
                            snapshot.session(),
                            expectedVersion + 1,
                            snapshot.title(),
                            snapshot.description(),
                            snapshot.localNote(),
                            snapshot.startedAt(),
                            snapshot.endedAt(),
                            snapshot.createdAt(),
                            snapshot.updatedAt(),
                            snapshot.deletedAt()));
            return true;
        }

        private static String key(AuthenticatedPrincipal principal, UUID problemSessionId) {
            return principal.userId() + ":" + problemSessionId;
        }
    }

    private static final class InMemoryBackgroundJobRepository implements BackgroundJobRepository {

        private final Set<String> keys = new HashSet<>();

        @Override
        public boolean insertIfAbsent(BackgroundJob job) {
            return keys.add(job.userId() + ":" + job.jobType() + ":" + job.aggregateType() + ":" + job.aggregateId());
        }

        @Override
        public List<BackgroundJob> claimReadyJobs(
                BackgroundJobType jobType, String leaseOwner, Instant now, Instant leaseExpiresAt, int limit) {
            throw new UnsupportedOperationException("Leasing is outside ProblemSessionSolutionService tests.");
        }

        @Override
        public boolean markSucceeded(UUID jobId, String leaseOwner, Instant completedAt) {
            throw new UnsupportedOperationException("Processing is outside ProblemSessionSolutionService tests.");
        }

        @Override
        public boolean markRetryableFailure(
                UUID jobId, String leaseOwner, String safeFailureCode, Instant nextAttemptAt, Instant now) {
            throw new UnsupportedOperationException("Processing is outside ProblemSessionSolutionService tests.");
        }

        @Override
        public boolean markTerminalFailure(UUID jobId, String leaseOwner, String safeFailureCode, Instant completedAt) {
            throw new UnsupportedOperationException("Processing is outside ProblemSessionSolutionService tests.");
        }

        int insertedCount() {
            return keys.size();
        }
    }

    private static final class InMemorySessionArtifactRepository implements SessionArtifactRepository {
        private final List<UUID> promotedProblemSessionIds = new ArrayList<>();

        @Override
        public void insert(AuthenticatedPrincipal principal, SessionArtifact artifact) {
            throw new UnsupportedOperationException("manual artifact insert is outside solution service tests.");
        }

        @Override
        public int promoteAssociatedCaptureEvents(AuthenticatedPrincipal principal, UUID problemSessionId) {
            promotedProblemSessionIds.add(problemSessionId);
            return 0;
        }

        @Override
        public List<SessionArtifact> findActiveByProblemSession(AuthenticatedPrincipal principal, UUID problemSessionId) {
            throw new UnsupportedOperationException("artifact reads are outside solution service tests.");
        }

        @Override
        public boolean markRemoved(
                AuthenticatedPrincipal principal, UUID problemSessionId, UUID artifactId, Instant removedAt) {
            throw new UnsupportedOperationException("artifact removal is outside solution service tests.");
        }

        private List<UUID> promoted() {
            return List.copyOf(promotedProblemSessionIds);
        }
    }
}
