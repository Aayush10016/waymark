package io.github.aayush10016.trail.problem.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.aayush10016.trail.identity.domain.model.AuthenticatedPrincipal;
import io.github.aayush10016.trail.problem.domain.model.FinalSolution;
import io.github.aayush10016.trail.problem.domain.model.ProblemSession;
import io.github.aayush10016.trail.problem.domain.model.ProblemSessionSnapshot;
import io.github.aayush10016.trail.problem.domain.model.ProblemSessionSource;
import io.github.aayush10016.trail.problem.domain.model.ProblemSessionStatus;
import io.github.aayush10016.trail.problem.domain.model.SessionArtifact;
import io.github.aayush10016.trail.problem.domain.model.SessionArtifactType;
import java.time.Instant;
import java.sql.Timestamp;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
class JdbcProblemRepositoriesTest {

    private static final String POSTGRES_PGVECTOR_IMAGE =
            "pgvector/pgvector:0.8.2-pg17@sha256:feb68f4f15446397d8cac7f4fe48fe4586de83160d1fc48b46283312d1a33966";

    @Container
    static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>(
                            DockerImageName.parse(POSTGRES_PGVECTOR_IMAGE)
                                    .asCompatibleSubstituteFor("postgres"))
                    .withDatabaseName("trail_problem_repository_test")
                    .withUsername("trail")
                    .withPassword("trail_local_password");

    private JdbcTemplate jdbcTemplate;
    private JdbcProblemSessionRepository sessionRepository;
    private JdbcSessionArtifactRepository artifactRepository;

    @BeforeEach
    void migrateFromEmptyDatabase() {
        var flyway =
                Flyway.configure()
                        .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                        .locations("classpath:db/migration")
                        .cleanDisabled(false)
                        .load();

        flyway.clean();
        flyway.migrate();

        var dataSource = new PGSimpleDataSource();
        dataSource.setUrl(postgres.getJdbcUrl());
        dataSource.setUser(postgres.getUsername());
        dataSource.setPassword(postgres.getPassword());

        jdbcTemplate = new JdbcTemplate(dataSource);
        sessionRepository = new JdbcProblemSessionRepository(jdbcTemplate);
        artifactRepository = new JdbcSessionArtifactRepository(jdbcTemplate);
    }

    @Test
    void persistsAndReadsProblemSessionWithinUserBoundary() {
        var principal = new AuthenticatedPrincipal(insertUser("problem-owner@example.com"));
        var otherPrincipal = new AuthenticatedPrincipal(insertUser("problem-other@example.com"));
        var snapshot = candidateSnapshot(UUID.randomUUID(), 0L, "Spring Security CORS not working");

        sessionRepository.insert(principal, snapshot);

        var stored = sessionRepository.findById(principal, snapshot.id()).orElseThrow();
        assertThat(stored.id()).isEqualTo(snapshot.id());
        assertThat(stored.session().status()).isEqualTo(snapshot.session().status());
        assertThat(stored.title()).isEqualTo(snapshot.title());
        assertThat(stored.version()).isEqualTo(snapshot.version());
        assertThat(sessionRepository.findById(otherPrincipal, snapshot.id())).isEmpty();
    }

    @Test
    void persistsSolvedSessionWithExactUserAuthoredSolution() {
        var principal = new AuthenticatedPrincipal(insertUser("solved-problem@example.com"));
        var authoredAt = Instant.parse("2026-07-13T09:30:00Z");
        var exactSolution = "Allowed localhost:3000 and registered the JWT filter before UsernamePasswordAuthenticationFilter.";
        var snapshot = new ProblemSessionSnapshot(
                UUID.randomUUID(),
                ProblemSessionSource.MANUAL,
                ProblemSession.rehydrate(
                        ProblemSessionStatus.SOLVED, FinalSolution.userAuthored(exactSolution, authoredAt)),
                0L,
                "Spring Security CORS not working",
                "Debugged CORS failure in local React + Spring Security setup.",
                "Exact local note",
                Instant.parse("2026-07-13T08:00:00Z"),
                Instant.parse("2026-07-13T09:30:00Z"),
                Instant.parse("2026-07-13T08:00:00Z"),
                Instant.parse("2026-07-13T09:30:00Z"),
                null);

        sessionRepository.insert(principal, snapshot);

        var stored = sessionRepository.findById(principal, snapshot.id()).orElseThrow();
        assertThat(stored.session().status()).isEqualTo(ProblemSessionStatus.SOLVED);
        assertThat(stored.session().finalSolution()).contains(FinalSolution.userAuthored(exactSolution, authoredAt));
    }

    @Test
    void updatesProblemSessionWithOptimisticConcurrency() {
        var principal = new AuthenticatedPrincipal(insertUser("concurrency-problem@example.com"));
        var problemSessionId = UUID.randomUUID();
        sessionRepository.insert(principal, candidateSnapshot(problemSessionId, 0L, "Initial title"));

        var firstRead = sessionRepository.findById(principal, problemSessionId).orElseThrow();
        var secondRead = sessionRepository.findById(principal, problemSessionId).orElseThrow();

        assertThat(sessionRepository.update(principal, reviewableSnapshot(firstRead), firstRead.version())).isTrue();
        assertThat(sessionRepository.update(principal, reviewableSnapshot(secondRead), secondRead.version())).isFalse();

        var stored = sessionRepository.findById(principal, problemSessionId).orElseThrow();
        assertThat(stored.version()).isEqualTo(1L);
        assertThat(stored.session().status()).isEqualTo(ProblemSessionStatus.REVIEWABLE);
    }

    @Test
    void persistsReadsAndRemovesArtifactsWithinUserBoundary() {
        var principal = new AuthenticatedPrincipal(insertUser("artifact-owner@example.com"));
        var otherPrincipal = new AuthenticatedPrincipal(insertUser("artifact-other@example.com"));
        var problemSessionId = UUID.randomUUID();
        var second = artifact(UUID.randomUUID(), problemSessionId, 2, "https://example.test/second");
        var first = artifact(UUID.randomUUID(), problemSessionId, 1, "https://example.test/first");
        sessionRepository.insert(principal, candidateSnapshot(problemSessionId, 0L, "Artifact ordering"));

        artifactRepository.insert(principal, second);
        artifactRepository.insert(principal, first);

        assertThat(artifactRepository.findActiveByProblemSession(principal, problemSessionId))
                .extracting(SessionArtifact::id)
                .containsExactly(first.id(), second.id());
        assertThat(artifactRepository.findActiveByProblemSession(otherPrincipal, problemSessionId)).isEmpty();

        var removedAt = Instant.now().plusSeconds(1);
        assertThat(artifactRepository.markRemoved(principal, problemSessionId, first.id(), removedAt)).isTrue();
        assertThat(artifactRepository.markRemoved(principal, problemSessionId, first.id(), removedAt)).isFalse();

        var replacement = artifact(UUID.randomUUID(), problemSessionId, 1, "https://example.test/replacement");
        artifactRepository.insert(principal, replacement);

        assertThat(artifactRepository.findActiveByProblemSession(principal, problemSessionId))
                .extracting(SessionArtifact::id)
                .containsExactly(replacement.id(), second.id());
    }

    @Test
    void promotesAssociatedCaptureEventsIntoOrderedProblemSessionArtifacts() {
        var principal = new AuthenticatedPrincipal(insertUser("capture-promotion@example.com"));
        var problemSessionId = UUID.randomUUID();
        sessionRepository.insert(principal, candidateSnapshot(problemSessionId, 0L, "Captured resources"));
        insertCaptureEvent(
                principal.userId(),
                "capture-search",
                problemSessionId,
                "SEARCH",
                "https://www.google.com/search?q=Spring%20Security%20CORS",
                "www.google.com",
                "Spring Security CORS - Google Search",
                "Spring Security CORS",
                Instant.parse("2026-07-13T08:01:00Z"));
        insertCaptureEvent(
                principal.userId(),
                "capture-docs",
                problemSessionId,
                "DOCUMENTATION",
                "https://docs.spring.io/spring-security/reference/servlet/integrations/cors.html",
                "docs.spring.io",
                "CORS :: Spring Security",
                null,
                Instant.parse("2026-07-13T08:02:00Z"));
        insertCaptureEvent(
                principal.userId(),
                "capture-unassociated",
                null,
                "DOCUMENTATION",
                "https://example.test/unassociated",
                "example.test",
                "Unassociated",
                null,
                Instant.parse("2026-07-13T08:03:00Z"));

        assertThat(artifactRepository.promoteAssociatedCaptureEvents(principal, problemSessionId)).isEqualTo(2);
        assertThat(artifactRepository.promoteAssociatedCaptureEvents(principal, problemSessionId)).isZero();

        assertThat(artifactRepository.findActiveByProblemSession(principal, problemSessionId))
                .satisfiesExactly(
                        searchArtifact -> {
                            assertThat(searchArtifact.position()).isEqualTo(1);
                            assertThat(searchArtifact.artifactType()).isEqualTo(SessionArtifactType.SEARCH_QUERY);
                            assertThat(searchArtifact.sanitizedUrl())
                                    .isEqualTo("https://www.google.com/search?q=Spring%20Security%20CORS");
                            assertThat(searchArtifact.queryText()).isEqualTo("Spring Security CORS");
                            assertThat(searchArtifact.sourceCaptureEventId()).isNotNull();
                        },
                        documentationArtifact -> {
                            assertThat(documentationArtifact.position()).isEqualTo(2);
                            assertThat(documentationArtifact.artifactType()).isEqualTo(SessionArtifactType.DOCUMENTATION);
                            assertThat(documentationArtifact.hostname()).isEqualTo("docs.spring.io");
                            assertThat(documentationArtifact.title()).isEqualTo("CORS :: Spring Security");
                            assertThat(documentationArtifact.queryText()).isNull();
                            assertThat(documentationArtifact.sourceCaptureEventId()).isNotNull();
                        });
    }

    private UUID insertUser(String email) {
        var userId = UUID.randomUUID();
        jdbcTemplate.update(
                """
                insert into users (
                    id,
                    email_normalized,
                    email_display,
                    password_hash,
                    password_hash_algorithm,
                    password_hash_parameters,
                    password_hash_version,
                    status
                ) values (?, ?, ?, ?, 'ARGON2ID', ?, 1, 'ACTIVE')
                """,
                userId,
                email,
                email,
                "$argon2id$v=19$m=65536,t=3,p=2$synthetic$safehash",
                "m=65536,t=3,p=2");
        return userId;
    }

    private void insertCaptureEvent(
            UUID userId,
            String clientEventId,
            UUID problemSessionId,
            String resourceType,
            String sanitizedUrl,
            String hostname,
            String title,
            String searchQuery,
            Instant occurredAt) {
        jdbcTemplate.update(
                """
                insert into capture_events (
                    id,
                    user_id,
                    client_event_id,
                    client_sequence,
                    client_instance_id,
                    source,
                    resource_type,
                    navigation_type,
                    sanitized_url,
                    canonical_url_hash,
                    hostname,
                    title,
                    search_engine,
                    search_query,
                    removed_query_parameters,
                    transition_qualifiers,
                    policy_version,
                    browser_event_time_ms,
                    occurred_at,
                    candidate_problem_session_id,
                    retention_expires_at
                ) values (
                    ?, ?, ?, ?, ?, 'WEB_NAVIGATION_COMMITTED', ?, 'link', ?, ?, ?, ?, ?, ?,
                    array[]::text[], array['from_address_bar']::text[], ?, ?, ?, ?, ?
                )
                """,
                UUID.randomUUID(),
                userId,
                clientEventId,
                1L,
                "extension-instance-1",
                resourceType,
                sanitizedUrl,
                sha256(sanitizedUrl),
                hostname,
                title,
                searchQuery == null ? null : "google",
                searchQuery,
                "url-query-sanitation-v1",
                100D,
                Timestamp.from(occurredAt),
                problemSessionId,
                Timestamp.from(occurredAt.plusSeconds(604800)));
    }

    private static ProblemSessionSnapshot candidateSnapshot(UUID id, long version, String title) {
        return new ProblemSessionSnapshot(
                id,
                ProblemSessionSource.MANUAL,
                ProblemSession.candidate(),
                version,
                title,
                "Problem description",
                "Local note",
                Instant.parse("2026-07-13T08:00:00Z"),
                null,
                Instant.parse("2026-07-13T08:00:00Z"),
                Instant.parse("2026-07-13T08:00:00Z"),
                null);
    }

    private static ProblemSessionSnapshot reviewableSnapshot(ProblemSessionSnapshot snapshot) {
        return new ProblemSessionSnapshot(
                snapshot.id(),
                snapshot.source(),
                snapshot.session().markReviewable(),
                snapshot.version(),
                snapshot.title(),
                snapshot.description(),
                snapshot.localNote(),
                snapshot.startedAt(),
                snapshot.endedAt(),
                snapshot.createdAt(),
                Instant.parse("2026-07-13T08:30:00Z"),
                snapshot.deletedAt());
    }

    private static SessionArtifact artifact(UUID id, UUID problemSessionId, int position, String url) {
        return new SessionArtifact(
                id,
                problemSessionId,
                position,
                SessionArtifactType.DOCUMENTATION,
                url,
                "example.test",
                "Example docs",
                null,
                null,
                Instant.parse("2026-07-13T08:05:00Z"),
                false,
                null,
                null);
    }

    private static byte[] sha256(String value) {
        try {
            return java.security.MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
