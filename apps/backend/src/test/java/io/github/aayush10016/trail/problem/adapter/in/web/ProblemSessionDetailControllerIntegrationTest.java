package io.github.aayush10016.trail.problem.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.aayush10016.trail.bootstrap.TrailApplication;
import io.github.aayush10016.trail.identity.domain.model.AuthenticatedPrincipal;
import io.github.aayush10016.trail.identity.security.JwtAccessTokenIssuer;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest(classes = TrailApplication.class)
@AutoConfigureMockMvc
@Testcontainers
class ProblemSessionDetailControllerIntegrationTest {

    private static final String POSTGRES_PGVECTOR_IMAGE =
            "pgvector/pgvector:0.8.2-pg17@sha256:feb68f4f15446397d8cac7f4fe48fe4586de83160d1fc48b46283312d1a33966";
    private static final KeyPair KEY_PAIR = generateKeyPair();

    @Container
    static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>(
                            DockerImageName.parse(POSTGRES_PGVECTOR_IMAGE)
                                    .asCompatibleSubstituteFor("postgres"))
                    .withDatabaseName("trail_problem_detail_api_test")
                    .withUsername("trail")
                    .withPassword("trail_local_password");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        registry.add("trail.database.url", postgres::getJdbcUrl);
        registry.add("trail.database.username", postgres::getUsername);
        registry.add("trail.database.password", postgres::getPassword);
        registry.add("trail.identity.jwt.key-id", () -> "test-key");
        registry.add("trail.identity.jwt.issuer", () -> "trail-test");
        registry.add("trail.identity.jwt.audience", () -> "trail-extension");
        registry.add("trail.identity.jwt.public-key-pem", ProblemSessionDetailControllerIntegrationTest::publicKeyPem);
    }

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
    }

    @Test
    void returnsOwnedProblemSessionDetailWithOrderedActiveArtifactsAndEtag() throws Exception {
        var userId = insertUser("detail-owner@example.com");
        var problemSessionId = UUID.fromString("40000000-0000-4000-8000-000000000001");
        insertCandidateSession(userId, problemSessionId, 3L);
        var removedArtifactId = UUID.fromString("50000000-0000-4000-8000-000000000099");
        insertArtifact(userId, problemSessionId, removedArtifactId, 1, "https://docs.example.test/removed", null);
        jdbcTemplate.update("update session_artifacts set removed_at = now(), updated_at = now() where id = ?", removedArtifactId);
        insertArtifact(
                userId,
                problemSessionId,
                UUID.fromString("50000000-0000-4000-8000-000000000002"),
                2,
                "https://github.com/example/repo",
                null);
        insertArtifact(
                userId,
                problemSessionId,
                UUID.fromString("50000000-0000-4000-8000-000000000001"),
                1,
                "https://docs.example.test/cors?topic=spring",
                "spring cors");

        mockMvc.perform(get("/api/v1/problem-sessions/{problemSessionId}", problemSessionId)
                        .header("Authorization", "Bearer " + tokenFor(userId)))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"3\""))
                .andExpect(jsonPath("$.id").value(problemSessionId.toString()))
                .andExpect(jsonPath("$.status").value("CANDIDATE"))
                .andExpect(jsonPath("$.source").value("MANUAL"))
                .andExpect(jsonPath("$.version").value(3))
                .andExpect(jsonPath("$.title").value("Spring Security CORS"))
                .andExpect(jsonPath("$.description").value("Debugged local CORS behavior."))
                .andExpect(jsonPath("$.localNote").value("Check filter order."))
                .andExpect(jsonPath("$.artifacts.length()").value(2))
                .andExpect(jsonPath("$.artifacts[0].position").value(1))
                .andExpect(jsonPath("$.artifacts[0].sanitizedUrl").value("https://docs.example.test/cors?topic=spring"))
                .andExpect(jsonPath("$.artifacts[0].queryText").value("spring cors"))
                .andExpect(jsonPath("$.artifacts[1].position").value(2))
                .andExpect(jsonPath("$.omittedArtifactCount").value(0))
                .andExpect(jsonPath("$.artifacts[0].sourceCaptureEventId").doesNotExist())
                .andExpect(jsonPath("$.artifacts[0].removedAt").doesNotExist());
    }

    @Test
    void returnsEmptyArtifactsForOwnedSessionWithoutEvidence() throws Exception {
        var userId = insertUser("empty-detail@example.com");
        var problemSessionId = UUID.fromString("40000000-0000-4000-8000-000000000002");
        insertCandidateSession(userId, problemSessionId, 0L);

        mockMvc.perform(get("/api/v1/problem-sessions/{problemSessionId}", problemSessionId)
                        .header("Authorization", "Bearer " + tokenFor(userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.artifacts").isArray())
                .andExpect(jsonPath("$.artifacts.length()").value(0))
                .andExpect(jsonPath("$.omittedArtifactCount").value(0));
    }

    @Test
    void returnsNotFoundForMissingAndCrossUserSessions() throws Exception {
        var ownerId = insertUser("detail-cross-owner@example.com");
        var otherUserId = insertUser("detail-cross-other@example.com");
        var problemSessionId = UUID.fromString("40000000-0000-4000-8000-000000000003");
        insertCandidateSession(ownerId, problemSessionId, 0L);

        mockMvc.perform(get("/api/v1/problem-sessions/{problemSessionId}", problemSessionId)
                        .header("Authorization", "Bearer " + tokenFor(otherUserId)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PROBLEM_SESSION_NOT_FOUND"));

        mockMvc.perform(get("/api/v1/problem-sessions/{problemSessionId}", UUID.randomUUID())
                        .header("Authorization", "Bearer " + tokenFor(otherUserId)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PROBLEM_SESSION_NOT_FOUND"));
    }

    @Test
    void rejectsMissingAndInvalidAuthentication() throws Exception {
        var problemSessionId = UUID.fromString("40000000-0000-4000-8000-000000000004");

        mockMvc.perform(get("/api/v1/problem-sessions/{problemSessionId}", problemSessionId))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));

        mockMvc.perform(get("/api/v1/problem-sessions/{problemSessionId}", problemSessionId)
                        .header("Authorization", "Bearer invalid-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
    }

    @Test
    void boundsArtifactResponseAndPreservesDeterministicOrderingAcrossReads() throws Exception {
        var userId = insertUser("bounded-detail@example.com");
        var problemSessionId = UUID.fromString("40000000-0000-4000-8000-000000000005");
        insertCandidateSession(userId, problemSessionId, 0L);
        for (var position = 1; position <= 102; position++) {
            insertArtifact(userId, problemSessionId, UUID.randomUUID(), position, "https://docs.example.test/" + position, null);
        }

        mockMvc.perform(get("/api/v1/problem-sessions/{problemSessionId}", problemSessionId)
                        .header("Authorization", "Bearer " + tokenFor(userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.artifacts.length()").value(100))
                .andExpect(jsonPath("$.artifacts[0].position").value(1))
                .andExpect(jsonPath("$.artifacts[99].position").value(100))
                .andExpect(jsonPath("$.omittedArtifactCount").value(2));

        mockMvc.perform(get("/api/v1/problem-sessions/{problemSessionId}", problemSessionId)
                        .header("Authorization", "Bearer " + tokenFor(userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.artifacts[0].position").value(1))
                .andExpect(jsonPath("$.artifacts[99].position").value(100));

        assertThat(problemSessionVersion(problemSessionId)).isEqualTo(0L);
    }

    @Test
    void isolatesArtifactsAcrossUsersAndSessions() throws Exception {
        var ownerId = insertUser("detail-isolated-owner@example.com");
        var otherUserId = insertUser("detail-isolated-other@example.com");
        var problemSessionId = UUID.fromString("40000000-0000-4000-8000-000000000006");
        var otherSessionId = UUID.fromString("40000000-0000-4000-8000-000000000007");
        insertCandidateSession(ownerId, problemSessionId, 0L);
        insertCandidateSession(ownerId, otherSessionId, 0L);
        insertCandidateSession(otherUserId, UUID.fromString("40000000-0000-4000-8000-000000000008"), 0L);
        insertArtifact(ownerId, problemSessionId, UUID.randomUUID(), 1, "https://docs.example.test/owned", null);
        insertArtifact(ownerId, otherSessionId, UUID.randomUUID(), 1, "https://docs.example.test/other-session", null);

        mockMvc.perform(get("/api/v1/problem-sessions/{problemSessionId}", problemSessionId)
                        .header("Authorization", "Bearer " + tokenFor(ownerId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.artifacts.length()").value(1))
                .andExpect(jsonPath("$.artifacts[0].sanitizedUrl").value("https://docs.example.test/owned"));
    }

    @Test
    void returnsDurableArtifactAfterRawCaptureRetentionDeletion() throws Exception {
        var userId = insertUser("detail-retention@example.com");
        var problemSessionId = UUID.fromString("40000000-0000-4000-8000-000000000009");
        var captureEventId = UUID.fromString("60000000-0000-4000-8000-000000000001");
        insertCandidateSession(userId, problemSessionId, 0L);
        insertCaptureEvent(userId, problemSessionId, captureEventId);
        insertArtifact(
                userId,
                problemSessionId,
                UUID.fromString("50000000-0000-4000-8000-000000000009"),
                1,
                "https://docs.example.test/durable",
                null,
                captureEventId);
        jdbcTemplate.update("delete from capture_events where id = ?", captureEventId);

        mockMvc.perform(get("/api/v1/problem-sessions/{problemSessionId}", problemSessionId)
                        .header("Authorization", "Bearer " + tokenFor(userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.artifacts.length()").value(1))
                .andExpect(jsonPath("$.artifacts[0].sanitizedUrl").value("https://docs.example.test/durable"));
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
                    status,
                    email_verified_at
                ) values (?, ?, ?, ?, 'ARGON2ID', ?, 1, 'ACTIVE', now())
                """,
                userId,
                email,
                email,
                "$argon2id$v=19$m=65536,t=3,p=2$synthetic$safehash",
                "m=65536,t=3,p=2");
        return userId;
    }

    private void insertCandidateSession(UUID userId, UUID problemSessionId, long version) {
        jdbcTemplate.update(
                """
                insert into problem_sessions (
                    id,
                    user_id,
                    status,
                    source,
                    version,
                    title,
                    description,
                    local_note,
                    started_at,
                    search_document
                ) values (?, ?, 'CANDIDATE', 'MANUAL', ?, 'Spring Security CORS', 'Debugged local CORS behavior.', 'Check filter order.', ?::timestamptz, 'Spring Security CORS')
                """,
                problemSessionId,
                userId,
                version,
                "2026-07-13T08:00:00Z");
    }

    private void insertArtifact(
            UUID userId, UUID problemSessionId, UUID artifactId, int position, String sanitizedUrl, String queryText) {
        insertArtifact(userId, problemSessionId, artifactId, position, sanitizedUrl, queryText, null);
    }

    private void insertArtifact(
            UUID userId,
            UUID problemSessionId,
            UUID artifactId,
            int position,
            String sanitizedUrl,
            String queryText,
            UUID sourceCaptureEventId) {
        jdbcTemplate.update(
                """
                insert into session_artifacts (
                    id,
                    user_id,
                    problem_session_id,
                    position,
                    artifact_type,
                    sanitized_url,
                    hostname,
                    title,
                    query_text,
                    source_capture_event_id,
                    occurred_at
                ) values (?, ?, ?, ?, ?, ?, 'docs.example.test', 'Example docs', ?, ?, ?::timestamptz)
                """,
                artifactId,
                userId,
                problemSessionId,
                position,
                queryText == null ? "DOCUMENTATION" : "SEARCH_QUERY",
                sanitizedUrl,
                queryText,
                sourceCaptureEventId,
                "2026-07-13T08:05:00Z");
    }

    private void insertCaptureEvent(UUID userId, UUID problemSessionId, UUID captureEventId) {
        jdbcTemplate.update(
                """
                insert into capture_events (
                    id,
                    user_id,
                    client_event_id,
                    client_instance_id,
                    source,
                    resource_type,
                    sanitized_url,
                    canonical_url_hash,
                    hostname,
                    title,
                    policy_version,
                    occurred_at,
                    retention_expires_at,
                    candidate_problem_session_id
                ) values (?, ?, 'client-event-1', 'client-instance-1', 'WEB_NAVIGATION_COMMITTED', 'DOCUMENTATION', 'https://docs.example.test/durable', decode(repeat('00', 32), 'hex'), 'docs.example.test', 'Example docs', 'capture-policy-v1', ?::timestamptz, ?::timestamptz, ?)
                """,
                captureEventId,
                userId,
                "2026-07-13T08:05:00Z",
                "2026-07-20T08:05:00Z",
                problemSessionId);
    }

    private long problemSessionVersion(UUID problemSessionId) {
        return jdbcTemplate.queryForObject(
                "select version from problem_sessions where id = ?", Long.class, problemSessionId);
    }

    private String tokenFor(UUID userId) {
        var issuer =
                new JwtAccessTokenIssuer(
                        KEY_PAIR.getPrivate(),
                        "test-key",
                        "trail-test",
                        "trail-extension",
                        Duration.ofMinutes(10),
                        Clock.systemUTC(),
                        () -> UUID.fromString("00000000-0000-4000-8000-000000000001"));
        return issuer.issue(new AuthenticatedPrincipal(userId)).token();
    }

    private static String publicKeyPem() {
        return "-----BEGIN PUBLIC KEY-----\n"
                + Base64.getMimeEncoder(64, "\n".getBytes(java.nio.charset.StandardCharsets.US_ASCII))
                        .encodeToString(KEY_PAIR.getPublic().getEncoded())
                + "\n-----END PUBLIC KEY-----";
    }

    private static KeyPair generateKeyPair() {
        try {
            var generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("RSA key generation unavailable", exception);
        }
    }
}
