package io.github.aayush10016.trail.problem.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.aayush10016.trail.identity.domain.model.AuthenticatedPrincipal;
import io.github.aayush10016.trail.problem.application.command.FinishProblemSessionCommand;
import io.github.aayush10016.trail.problem.application.command.StartProblemSessionCommand;
import io.github.aayush10016.trail.problem.domain.model.FinalSolution;
import io.github.aayush10016.trail.problem.domain.model.ProblemSession;
import io.github.aayush10016.trail.problem.domain.model.ProblemSessionSnapshot;
import io.github.aayush10016.trail.problem.domain.model.ProblemSessionSource;
import io.github.aayush10016.trail.problem.domain.model.ProblemSessionStatus;
import io.github.aayush10016.trail.problem.domain.model.ProblemSessionTransitionException;
import io.github.aayush10016.trail.problem.domain.port.ProblemSessionRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ManualProblemSessionServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-13T08:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final AuthenticatedPrincipal PRINCIPAL =
            new AuthenticatedPrincipal(UUID.fromString("10000000-0000-4000-8000-000000000001"));

    private final InMemoryProblemSessionRepository repository = new InMemoryProblemSessionRepository();
    private final ManualProblemSessionService service = new ManualProblemSessionService(repository, CLOCK);

    @Test
    void startsManualCandidateWithUserOwnedInitialState() {
        var problemSessionId = UUID.fromString("20000000-0000-4000-8000-000000000001");

        var result = service.start(PRINCIPAL, new StartProblemSessionCommand(problemSessionId, "  Spring CORS  ", null));

        assertThat(result.id()).isEqualTo(problemSessionId);
        assertThat(result.status()).isEqualTo(ProblemSessionStatus.CANDIDATE);
        assertThat(result.source()).isEqualTo(ProblemSessionSource.MANUAL);
        assertThat(result.version()).isZero();
        assertThat(result.title()).isEqualTo("Spring CORS");
        assertThat(result.startedAt()).isEqualTo(NOW);
    }

    @Test
    void startIsIdempotentForSameUserAndProblemSessionId() {
        var problemSessionId = UUID.fromString("20000000-0000-4000-8000-000000000002");

        var first = service.start(PRINCIPAL, new StartProblemSessionCommand(problemSessionId, "First", null));
        var second = service.start(PRINCIPAL, new StartProblemSessionCommand(problemSessionId, "Second", null));

        assertThat(second).isEqualTo(first);
        assertThat(repository.count()).isEqualTo(1);
    }

    @Test
    void finishMovesCandidateToReviewableAndIncrementsVersion() {
        var problemSessionId = UUID.fromString("20000000-0000-4000-8000-000000000003");
        service.start(PRINCIPAL, new StartProblemSessionCommand(problemSessionId, null, null));

        var result = service.finish(
                PRINCIPAL,
                new FinishProblemSessionCommand(problemSessionId, 0L, Instant.parse("2026-07-13T08:05:00Z")));

        assertThat(result.status()).isEqualTo(ProblemSessionStatus.REVIEWABLE);
        assertThat(result.version()).isEqualTo(1L);
        assertThat(result.endedAt()).isEqualTo(Instant.parse("2026-07-13T08:05:00Z"));
    }

    @Test
    void finishIsIdempotentWhenAlreadyReviewable() {
        var problemSessionId = UUID.fromString("20000000-0000-4000-8000-000000000004");
        service.start(PRINCIPAL, new StartProblemSessionCommand(problemSessionId, null, null));
        var first = service.finish(
                PRINCIPAL,
                new FinishProblemSessionCommand(problemSessionId, 0L, Instant.parse("2026-07-13T08:05:00Z")));

        var second = service.finish(
                PRINCIPAL,
                new FinishProblemSessionCommand(problemSessionId, 0L, Instant.parse("2026-07-13T08:06:00Z")));

        assertThat(second).isEqualTo(first);
        assertThat(repository.findById(PRINCIPAL, problemSessionId).orElseThrow().version()).isEqualTo(1L);
    }

    @Test
    void rejectsStaleCandidateVersion() {
        var problemSessionId = UUID.fromString("20000000-0000-4000-8000-000000000005");
        service.start(PRINCIPAL, new StartProblemSessionCommand(problemSessionId, null, null));

        assertThatThrownBy(() -> service.finish(
                        PRINCIPAL,
                        new FinishProblemSessionCommand(problemSessionId, 1L, Instant.parse("2026-07-13T08:05:00Z"))))
                .isInstanceOf(ProblemSessionVersionConflictException.class);
    }

    @Test
    void rejectsFinishBeforeStart() {
        var problemSessionId = UUID.fromString("20000000-0000-4000-8000-000000000006");
        service.start(PRINCIPAL, new StartProblemSessionCommand(problemSessionId, null, NOW));

        assertThatThrownBy(() -> service.finish(
                        PRINCIPAL,
                        new FinishProblemSessionCommand(problemSessionId, 0L, NOW.minusSeconds(1))))
                .isInstanceOf(InvalidProblemSessionCommandException.class);
    }

    @Test
    void rejectsTerminalStateFinish() {
        var problemSessionId = UUID.fromString("20000000-0000-4000-8000-000000000007");
        repository.insert(
                PRINCIPAL,
                snapshot(
                        problemSessionId,
                        ProblemSession.rehydrate(
                                ProblemSessionStatus.SOLVED,
                                FinalSolution.userAuthored(
                                        "Allowed localhost:3000 in CorsConfiguration.",
                                        Instant.parse("2026-07-13T08:10:00Z"))),
                        2L,
                        NOW,
                        Instant.parse("2026-07-13T08:10:00Z")));

        assertThatThrownBy(() -> service.finish(
                        PRINCIPAL,
                        new FinishProblemSessionCommand(problemSessionId, 2L, Instant.parse("2026-07-13T08:15:00Z"))))
                .isInstanceOf(ProblemSessionTransitionException.class);
    }

    @Test
    void returnsNotFoundForOtherUsers() {
        var problemSessionId = UUID.fromString("20000000-0000-4000-8000-000000000008");
        service.start(PRINCIPAL, new StartProblemSessionCommand(problemSessionId, null, null));
        var otherPrincipal = new AuthenticatedPrincipal(UUID.fromString("10000000-0000-4000-8000-000000000002"));

        assertThatThrownBy(() -> service.finish(
                        otherPrincipal,
                        new FinishProblemSessionCommand(problemSessionId, 0L, Instant.parse("2026-07-13T08:05:00Z"))))
                .isInstanceOf(ProblemSessionNotFoundException.class);
    }

    private static ProblemSessionSnapshot snapshot(
            UUID problemSessionId, ProblemSession session, long version, Instant startedAt, Instant endedAt) {
        return new ProblemSessionSnapshot(
                problemSessionId,
                ProblemSessionSource.MANUAL,
                session,
                version,
                null,
                null,
                null,
                startedAt,
                endedAt,
                NOW,
                endedAt == null ? NOW : endedAt,
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

        int count() {
            return snapshots.size();
        }

        private static String key(AuthenticatedPrincipal principal, UUID problemSessionId) {
            return principal.userId() + ":" + problemSessionId;
        }
    }
}
