package io.github.aayush10016.trail.problem.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.aayush10016.trail.identity.domain.model.AuthenticatedPrincipal;
import io.github.aayush10016.trail.problem.domain.model.ProblemSession;
import io.github.aayush10016.trail.problem.domain.model.ProblemSessionSnapshot;
import io.github.aayush10016.trail.problem.domain.model.ProblemSessionSource;
import io.github.aayush10016.trail.problem.domain.model.SessionArtifact;
import io.github.aayush10016.trail.problem.domain.model.SessionArtifactType;
import io.github.aayush10016.trail.problem.domain.port.ProblemSessionRepository;
import io.github.aayush10016.trail.problem.domain.port.SessionArtifactRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ProblemSessionDetailQueryServiceTest {

    private static final AuthenticatedPrincipal PRINCIPAL =
            new AuthenticatedPrincipal(UUID.fromString("10000000-0000-4000-8000-000000000001"));
    private static final UUID PROBLEM_SESSION_ID = UUID.fromString("20000000-0000-4000-8000-000000000001");
    private static final Instant NOW = Instant.parse("2026-07-16T08:00:00Z");

    @Test
    void returnsBoundedUserOwnedDetailWithStableArtifactOrder() {
        var sessionRepository = new InMemoryProblemSessionRepository(candidateSnapshot());
        var artifactRepository = new InMemorySessionArtifactRepository(List.of(
                artifact(1, "https://docs.example.test/first"),
                artifact(2, "https://github.com/example/repo")));
        var service = new ProblemSessionDetailQueryService(sessionRepository, artifactRepository);

        var detail = service.getDetail(PRINCIPAL, PROBLEM_SESSION_ID);

        assertThat(detail.id()).isEqualTo(PROBLEM_SESSION_ID);
        assertThat(detail.status()).isEqualTo("CANDIDATE");
        assertThat(detail.source()).isEqualTo("MANUAL");
        assertThat(detail.version()).isZero();
        assertThat(detail.artifacts())
                .extracting(artifact -> artifact.position())
                .containsExactly(1, 2);
        assertThat(detail.omittedArtifactCount()).isZero();
        assertThat(sessionRepository.updated()).isFalse();
    }

    @Test
    void boundsArtifactResponseAndReportsOmittedCount() {
        var artifacts = new ArrayList<SessionArtifact>();
        for (var position = 1; position <= ProblemSessionDetailQueryService.MAXIMUM_RETURNED_ARTIFACTS + 2; position++) {
            artifacts.add(artifact(position, "https://docs.example.test/" + position));
        }
        var service = new ProblemSessionDetailQueryService(
                new InMemoryProblemSessionRepository(candidateSnapshot()), new InMemorySessionArtifactRepository(artifacts));

        var detail = service.getDetail(PRINCIPAL, PROBLEM_SESSION_ID);

        assertThat(detail.artifacts()).hasSize(ProblemSessionDetailQueryService.MAXIMUM_RETURNED_ARTIFACTS);
        assertThat(detail.omittedArtifactCount()).isEqualTo(2);
        assertThat(detail.artifacts().get(detail.artifacts().size() - 1).position())
                .isEqualTo(ProblemSessionDetailQueryService.MAXIMUM_RETURNED_ARTIFACTS);
    }

    @Test
    void failsMissingSessionWithoutReadingArtifacts() {
        var artifactRepository = new InMemorySessionArtifactRepository(List.of(artifact(1, "https://docs.example.test/first")));
        var service = new ProblemSessionDetailQueryService(new InMemoryProblemSessionRepository(null), artifactRepository);

        assertThatThrownBy(() -> service.getDetail(PRINCIPAL, PROBLEM_SESSION_ID))
                .isInstanceOf(ProblemSessionNotFoundException.class);
        assertThat(artifactRepository.reads()).isZero();
    }

    private static ProblemSessionSnapshot candidateSnapshot() {
        return new ProblemSessionSnapshot(
                PROBLEM_SESSION_ID,
                ProblemSessionSource.MANUAL,
                ProblemSession.candidate(),
                0L,
                "Spring Security CORS",
                "Debugged local CORS behavior.",
                "Check filter order.",
                NOW,
                null,
                NOW,
                NOW,
                null);
    }

    private static SessionArtifact artifact(int position, String sanitizedUrl) {
        return new SessionArtifact(
                UUID.randomUUID(),
                PROBLEM_SESSION_ID,
                position,
                SessionArtifactType.DOCUMENTATION,
                sanitizedUrl,
                "docs.example.test",
                "Example docs",
                null,
                UUID.randomUUID(),
                NOW.plusSeconds(position),
                false,
                null,
                null);
    }

    private static final class InMemoryProblemSessionRepository implements ProblemSessionRepository {
        private final ProblemSessionSnapshot snapshot;
        private boolean updated;

        private InMemoryProblemSessionRepository(ProblemSessionSnapshot snapshot) {
            this.snapshot = snapshot;
        }

        @Override
        public void insert(AuthenticatedPrincipal principal, ProblemSessionSnapshot snapshot) {
            throw new UnsupportedOperationException("insert is not used by detail queries");
        }

        @Override
        public Optional<ProblemSessionSnapshot> findById(AuthenticatedPrincipal principal, UUID problemSessionId) {
            if (!PRINCIPAL.equals(principal) || !PROBLEM_SESSION_ID.equals(problemSessionId)) {
                return Optional.empty();
            }
            return Optional.ofNullable(snapshot);
        }

        @Override
        public boolean update(AuthenticatedPrincipal principal, ProblemSessionSnapshot snapshot, long expectedVersion) {
            updated = true;
            return false;
        }

        private boolean updated() {
            return updated;
        }
    }

    private static final class InMemorySessionArtifactRepository implements SessionArtifactRepository {
        private final List<SessionArtifact> artifacts;
        private int reads;

        private InMemorySessionArtifactRepository(List<SessionArtifact> artifacts) {
            this.artifacts = List.copyOf(artifacts);
        }

        @Override
        public void insert(AuthenticatedPrincipal principal, SessionArtifact artifact) {
            throw new UnsupportedOperationException("insert is not used by detail queries");
        }

        @Override
        public List<SessionArtifact> findActiveByProblemSession(
                AuthenticatedPrincipal principal, UUID problemSessionId) {
            reads++;
            return artifacts;
        }

        @Override
        public int promoteAssociatedCaptureEvents(AuthenticatedPrincipal principal, UUID problemSessionId) {
            throw new UnsupportedOperationException("promotion is not used by detail queries");
        }

        @Override
        public boolean markRemoved(
                AuthenticatedPrincipal principal, UUID problemSessionId, UUID artifactId, Instant removedAt) {
            throw new UnsupportedOperationException("markRemoved is not used by detail queries");
        }

        private int reads() {
            return reads;
        }
    }
}
