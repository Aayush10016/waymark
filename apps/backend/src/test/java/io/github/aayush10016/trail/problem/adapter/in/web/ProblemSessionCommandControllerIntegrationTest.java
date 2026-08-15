package io.github.aayush10016.trail.problem.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import java.time.Instant;
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
class ProblemSessionCommandControllerIntegrationTest {

    private static final String POSTGRES_PGVECTOR_IMAGE =
            "pgvector/pgvector:0.8.2-pg17@sha256:feb68f4f15446397d8cac7f4fe48fe4586de83160d1fc48b46283312d1a33966";
    private static final KeyPair KEY_PAIR = generateKeyPair();

    @Container
    static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>(
                            DockerImageName.parse(POSTGRES_PGVECTOR_IMAGE)
                                    .asCompatibleSubstituteFor("postgres"))
                    .withDatabaseName("trail_problem_api_test")
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
        registry.add("trail.identity.jwt.public-key-pem", ProblemSessionCommandControllerIntegrationTest::publicKeyPem);
        registry.add("trail.background-jobs.scheduler.enabled", () -> "false");
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
    void rejectsMissingAuthenticationForStart() throws Exception {
        mockMvc.perform(post("/api/v1/problem-sessions")
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
    }

    @Test
    void rejectsUnknownFieldsOnProblemSessionCommandRequests() throws Exception {
        var userId = insertUser("problem-mass-assignment@example.com");
        var problemSessionId = UUID.fromString("30000000-0000-4000-8000-000000000065");

        mockMvc.perform(post("/api/v1/problem-sessions")
                        .header("Authorization", "Bearer " + tokenFor(userId))
                        .contentType("application/json")
                        .content("""
                                {
                                  "problemSessionId": "%s",
                                  "title": "Spring Security CORS",
                                  "userId": "%s",
                                  "status": "SOLVED"
                                }
                                """.formatted(problemSessionId, userId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PROBLEM_SESSION_INVALID_REQUEST"));

        startSession(userId, problemSessionId);

        mockMvc.perform(post("/api/v1/problem-sessions/{problemSessionId}/finish", problemSessionId)
                        .header("Authorization", "Bearer " + tokenFor(userId))
                        .header("If-Match", "\"0\"")
                        .contentType("application/json")
                        .content("""
                                {
                                  "finishedAt": "2026-07-13T08:05:00Z",
                                  "version": 99
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PROBLEM_SESSION_INVALID_REQUEST"));

        finishSession(userId, problemSessionId, "\"0\"");

        mockMvc.perform(put("/api/v1/problem-sessions/{problemSessionId}/solution", problemSessionId)
                        .header("Authorization", "Bearer " + tokenFor(userId))
                        .header("If-Match", "\"1\"")
                        .header("Idempotency-Key", "solution-mass-assignment-key")
                        .contentType("application/json")
                        .content("""
                                {
                                  "finalSolution": "Solved.",
                                  "finalSolutionAuthor": "AI"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PROBLEM_SESSION_INVALID_REQUEST"));
    }

    @Test
    void startsManualProblemSessionIdempotently() throws Exception {
        var userId = insertUser("manual-start@example.com");
        var problemSessionId = UUID.fromString("30000000-0000-4000-8000-000000000001");

        mockMvc.perform(post("/api/v1/problem-sessions")
                        .header("Authorization", "Bearer " + tokenFor(userId))
                        .contentType("application/json")
                        .content(startJson(problemSessionId, "Spring Security CORS")))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"0\""))
                .andExpect(jsonPath("$.id").value(problemSessionId.toString()))
                .andExpect(jsonPath("$.status").value("CANDIDATE"))
                .andExpect(jsonPath("$.source").value("MANUAL"))
                .andExpect(jsonPath("$.version").value(0))
                .andExpect(jsonPath("$.title").value("Spring Security CORS"));

        mockMvc.perform(post("/api/v1/problem-sessions")
                        .header("Authorization", "Bearer " + tokenFor(userId))
                        .contentType("application/json")
                        .content(startJson(problemSessionId, "Ignored title")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Spring Security CORS"));

        assertThat(problemSessionCount()).isEqualTo(1);
    }

    @Test
    void finishesManualProblemSessionWithVersionPrecondition() throws Exception {
        var userId = insertUser("manual-finish@example.com");
        var problemSessionId = UUID.fromString("30000000-0000-4000-8000-000000000002");
        startSession(userId, problemSessionId);

        mockMvc.perform(post("/api/v1/problem-sessions/{problemSessionId}/finish", problemSessionId)
                        .header("Authorization", "Bearer " + tokenFor(userId))
                        .header("If-Match", "\"0\"")
                        .contentType("application/json")
                        .content(finishJson("2026-07-13T08:05:00Z")))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"1\""))
                .andExpect(jsonPath("$.status").value("REVIEWABLE"))
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.endedAt").value("2026-07-13T08:05:00Z"));
    }

    @Test
    void finishIsIdempotentAfterReviewableState() throws Exception {
        var userId = insertUser("manual-finish-idempotent@example.com");
        var problemSessionId = UUID.fromString("30000000-0000-4000-8000-000000000003");
        startSession(userId, problemSessionId);
        finishSession(userId, problemSessionId, "\"0\"");

        mockMvc.perform(post("/api/v1/problem-sessions/{problemSessionId}/finish", problemSessionId)
                        .header("Authorization", "Bearer " + tokenFor(userId))
                        .header("If-Match", "\"0\"")
                        .contentType("application/json")
                        .content(finishJson("2026-07-13T08:06:00Z")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REVIEWABLE"))
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.endedAt").value("2026-07-13T08:05:00Z"));
    }

    @Test
    void rejectsFinishWithoutIfMatch() throws Exception {
        var userId = insertUser("missing-if-match@example.com");
        var problemSessionId = UUID.fromString("30000000-0000-4000-8000-000000000004");
        startSession(userId, problemSessionId);

        mockMvc.perform(post("/api/v1/problem-sessions/{problemSessionId}/finish", problemSessionId)
                        .header("Authorization", "Bearer " + tokenFor(userId))
                        .contentType("application/json")
                        .content(finishJson("2026-07-13T08:05:00Z")))
                .andExpect(status().isPreconditionRequired())
                .andExpect(jsonPath("$.code").value("PROBLEM_SESSION_VERSION_REQUIRED"));
    }

    @Test
    void rejectsStaleFinishVersionForCandidate() throws Exception {
        var userId = insertUser("stale-version@example.com");
        var problemSessionId = UUID.fromString("30000000-0000-4000-8000-000000000005");
        startSession(userId, problemSessionId);

        mockMvc.perform(post("/api/v1/problem-sessions/{problemSessionId}/finish", problemSessionId)
                        .header("Authorization", "Bearer " + tokenFor(userId))
                        .header("If-Match", "\"1\"")
                        .contentType("application/json")
                        .content(finishJson("2026-07-13T08:05:00Z")))
                .andExpect(status().isPreconditionFailed())
                .andExpect(jsonPath("$.code").value("PROBLEM_SESSION_VERSION_CONFLICT"));
    }

    @Test
    void doesNotRevealCrossUserSessionAccess() throws Exception {
        var ownerId = insertUser("owner@example.com");
        var otherUserId = insertUser("other@example.com");
        var problemSessionId = UUID.fromString("30000000-0000-4000-8000-000000000006");
        startSession(ownerId, problemSessionId);

        mockMvc.perform(post("/api/v1/problem-sessions/{problemSessionId}/finish", problemSessionId)
                        .header("Authorization", "Bearer " + tokenFor(otherUserId))
                        .header("If-Match", "\"0\"")
                        .contentType("application/json")
                        .content(finishJson("2026-07-13T08:05:00Z")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PROBLEM_SESSION_NOT_FOUND"));
    }

    @Test
    void rejectsIllegalFinishTransitionFromSolvedState() throws Exception {
        var userId = insertUser("solved-finish@example.com");
        var problemSessionId = UUID.fromString("30000000-0000-4000-8000-000000000007");
        insertSolvedSession(userId, problemSessionId);

        mockMvc.perform(post("/api/v1/problem-sessions/{problemSessionId}/finish", problemSessionId)
                        .header("Authorization", "Bearer " + tokenFor(userId))
                        .header("If-Match", "\"0\"")
                        .contentType("application/json")
                        .content(finishJson("2026-07-13T08:15:00Z")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PROBLEM_SESSION_ILLEGAL_TRANSITION"));
    }

    @Test
    void savesSolutionWithExactTextAuthorshipVersionAndDurableJob() throws Exception {
        var userId = insertUser("solution-save@example.com");
        var problemSessionId = UUID.fromString("30000000-0000-4000-8000-000000000050");
        insertReviewableSession(userId, problemSessionId, 1L);
        var exactSolution = "  Allowed localhost:3000 and placed the JWT filter before UsernamePasswordAuthenticationFilter.  ";

        mockMvc.perform(put("/api/v1/problem-sessions/{problemSessionId}/solution", problemSessionId)
                        .header("Authorization", "Bearer " + tokenFor(userId))
                        .header("If-Match", "\"1\"")
                        .header("Idempotency-Key", "solution-save-key")
                        .contentType("application/json")
                        .content(solutionJson(exactSolution)))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"2\""))
                .andExpect(jsonPath("$.id").value(problemSessionId.toString()))
                .andExpect(jsonPath("$.status").value("SOLVED"))
                .andExpect(jsonPath("$.version").value(2))
                .andExpect(jsonPath("$.finalSolution").value(exactSolution))
                .andExpect(jsonPath("$.finalSolutionAuthor").value("USER"));

        assertThat(storedFinalSolution(problemSessionId)).isEqualTo(exactSolution);
        assertThat(storedSolutionAuthor(problemSessionId)).isEqualTo("USER");
        assertThat(backgroundJobCount(problemSessionId)).isEqualTo(2);
    }

    @Test
    void solutionRetryIsIdempotentAndDoesNotCreateDuplicateJob() throws Exception {
        var userId = insertUser("solution-retry@example.com");
        var problemSessionId = UUID.fromString("30000000-0000-4000-8000-000000000051");
        var exactSolution = "Configured CORS and JWT filter ordering.";
        insertReviewableSession(userId, problemSessionId, 1L);
        saveSolution(userId, problemSessionId, "\"1\"", "solution-retry-key", exactSolution);

        mockMvc.perform(put("/api/v1/problem-sessions/{problemSessionId}/solution", problemSessionId)
                        .header("Authorization", "Bearer " + tokenFor(userId))
                        .header("If-Match", "\"1\"")
                        .header("Idempotency-Key", "solution-retry-key")
                        .contentType("application/json")
                        .content(solutionJson(exactSolution)))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"2\""))
                .andExpect(jsonPath("$.status").value("SOLVED"))
                .andExpect(jsonPath("$.version").value(2));

        assertThat(backgroundJobCount(problemSessionId)).isEqualTo(2);
    }

    @Test
    void rejectsMismatchedSolutionRetryAndStaleUnsolvedVersion() throws Exception {
        var userId = insertUser("solution-conflict@example.com");
        var problemSessionId = UUID.fromString("30000000-0000-4000-8000-000000000052");
        insertReviewableSession(userId, problemSessionId, 1L);

        mockMvc.perform(put("/api/v1/problem-sessions/{problemSessionId}/solution", problemSessionId)
                        .header("Authorization", "Bearer " + tokenFor(userId))
                        .header("If-Match", "\"0\"")
                        .header("Idempotency-Key", "solution-stale-key")
                        .contentType("application/json")
                        .content(solutionJson("Solved.")))
                .andExpect(status().isPreconditionFailed())
                .andExpect(jsonPath("$.code").value("PROBLEM_SESSION_VERSION_CONFLICT"));

        saveSolution(userId, problemSessionId, "\"1\"", "solution-conflict-key", "Original solution.");

        mockMvc.perform(put("/api/v1/problem-sessions/{problemSessionId}/solution", problemSessionId)
                        .header("Authorization", "Bearer " + tokenFor(userId))
                        .header("If-Match", "\"1\"")
                        .header("Idempotency-Key", "solution-conflict-key")
                        .contentType("application/json")
                        .content(solutionJson("Different solution.")))
                .andExpect(status().isPreconditionFailed())
                .andExpect(jsonPath("$.code").value("PROBLEM_SESSION_VERSION_CONFLICT"));

        assertThat(backgroundJobCount(problemSessionId)).isEqualTo(2);
    }

    @Test
    void solutionEndpointEnforcesAuthenticationOwnershipAndPreconditions() throws Exception {
        var ownerId = insertUser("solution-owner@example.com");
        var otherUserId = insertUser("solution-other@example.com");
        var problemSessionId = UUID.fromString("30000000-0000-4000-8000-000000000053");
        insertReviewableSession(ownerId, problemSessionId, 1L);

        mockMvc.perform(put("/api/v1/problem-sessions/{problemSessionId}/solution", problemSessionId)
                        .header("If-Match", "\"1\"")
                        .header("Idempotency-Key", "solution-auth-key")
                        .contentType("application/json")
                        .content(solutionJson("Solved.")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));

        mockMvc.perform(put("/api/v1/problem-sessions/{problemSessionId}/solution", problemSessionId)
                        .header("Authorization", "Bearer invalid")
                        .header("If-Match", "\"1\"")
                        .header("Idempotency-Key", "solution-invalid-token-key")
                        .contentType("application/json")
                        .content(solutionJson("Solved.")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));

        mockMvc.perform(put("/api/v1/problem-sessions/{problemSessionId}/solution", problemSessionId)
                        .header("Authorization", "Bearer " + tokenFor(otherUserId))
                        .header("If-Match", "\"1\"")
                        .header("Idempotency-Key", "solution-other-key")
                        .contentType("application/json")
                        .content(solutionJson("Solved.")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PROBLEM_SESSION_NOT_FOUND"));

        mockMvc.perform(put("/api/v1/problem-sessions/{problemSessionId}/solution", problemSessionId)
                        .header("Authorization", "Bearer " + tokenFor(ownerId))
                        .header("Idempotency-Key", "solution-missing-if-match-key")
                        .contentType("application/json")
                        .content(solutionJson("Solved.")))
                .andExpect(status().isPreconditionRequired())
                .andExpect(jsonPath("$.code").value("PROBLEM_SESSION_VERSION_REQUIRED"));

        mockMvc.perform(put("/api/v1/problem-sessions/{problemSessionId}/solution", problemSessionId)
                        .header("Authorization", "Bearer " + tokenFor(ownerId))
                        .header("If-Match", "\"1\"")
                        .contentType("application/json")
                        .content(solutionJson("Solved.")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PROBLEM_SESSION_IDEMPOTENCY_KEY_REQUIRED"));

        assertThat(backgroundJobCount(problemSessionId)).isZero();
    }

    @Test
    void rejectsIllegalSolutionStateAndBlankSolution() throws Exception {
        var userId = insertUser("solution-invalid@example.com");
        var problemSessionId = UUID.fromString("30000000-0000-4000-8000-000000000054");
        startSession(userId, problemSessionId);

        mockMvc.perform(put("/api/v1/problem-sessions/{problemSessionId}/solution", problemSessionId)
                        .header("Authorization", "Bearer " + tokenFor(userId))
                        .header("If-Match", "\"0\"")
                        .header("Idempotency-Key", "solution-illegal-state-key")
                        .contentType("application/json")
                        .content(solutionJson("Solved.")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PROBLEM_SESSION_ILLEGAL_TRANSITION"));

        finishSession(userId, problemSessionId, "\"0\"");

        mockMvc.perform(put("/api/v1/problem-sessions/{problemSessionId}/solution", problemSessionId)
                        .header("Authorization", "Bearer " + tokenFor(userId))
                        .header("If-Match", "\"1\"")
                        .header("Idempotency-Key", "solution-blank-key")
                        .contentType("application/json")
                        .content(solutionJson("   ")))
                .andExpect(status().isBadRequest());

        assertThat(backgroundJobCount(problemSessionId)).isZero();
    }

    @Test
    void rollsBackSolvedTransitionWhenJobInsertFails() throws Exception {
        var userId = insertUser("solution-rollback@example.com");
        var problemSessionId = UUID.fromString("30000000-0000-4000-8000-000000000055");
        insertReviewableSession(userId, problemSessionId, 1L);
        jdbcTemplate.execute("alter table background_jobs add constraint ck_background_jobs_test_failure check (false)");

        mockMvc.perform(put("/api/v1/problem-sessions/{problemSessionId}/solution", problemSessionId)
                        .header("Authorization", "Bearer " + tokenFor(userId))
                        .header("If-Match", "\"1\"")
                        .header("Idempotency-Key", "solution-rollback-key")
                        .contentType("application/json")
                        .content(solutionJson("Solved.")))
                .andExpect(status().is5xxServerError());

        assertThat(problemSessionStatus(problemSessionId)).isEqualTo("REVIEWABLE");
        assertThat(storedFinalSolution(problemSessionId)).isNull();
        assertThat(backgroundJobCount(problemSessionId)).isZero();
    }

    @Test
    void deletesOwnedProblemSessionAndAllActiveDerivedRecords() throws Exception {
        var userId = insertUser("delete-session-owner@example.com");
        var problemSessionId = UUID.fromString("30000000-0000-4000-8000-000000000060");
        var siblingSessionId = UUID.fromString("30000000-0000-4000-8000-000000000061");
        insertSolvedSession(userId, problemSessionId);
        insertSolvedSession(userId, siblingSessionId);
        insertSessionArtifact(userId, problemSessionId);
        insertCaptureEvent(userId, problemSessionId);
        insertBackgroundJob(userId, problemSessionId);
        insertEmbeddingGeneration();
        insertAIDerivation(userId, problemSessionId);
        insertProblemEmbedding(userId, problemSessionId);
        insertRecommendationEvent(userId, problemSessionId, siblingSessionId);

        mockMvc.perform(delete("/api/v1/problem-sessions/{problemSessionId}", problemSessionId)
                        .header("Authorization", "Bearer " + tokenFor(userId)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/problem-sessions/{problemSessionId}", problemSessionId)
                        .header("Authorization", "Bearer " + tokenFor(userId)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PROBLEM_SESSION_NOT_FOUND"));

        assertThat(rowCount("problem_sessions", problemSessionId)).isZero();
        assertThat(rowCount("session_artifacts", problemSessionId)).isZero();
        assertThat(rowCount("ai_derivations", problemSessionId)).isZero();
        assertThat(rowCount("problem_embeddings", problemSessionId)).isZero();
        assertThat(rowCount("recommendation_events", problemSessionId)).isZero();
        assertThat(rowCount("background_jobs", problemSessionId)).isZero();
        assertThat(rowCount("capture_events", problemSessionId)).isZero();
    }

    @Test
    void deletionDoesNotRevealCrossUserSessions() throws Exception {
        var ownerId = insertUser("delete-owner@example.com");
        var otherUserId = insertUser("delete-other@example.com");
        var problemSessionId = UUID.fromString("30000000-0000-4000-8000-000000000062");
        insertSolvedSession(ownerId, problemSessionId);

        mockMvc.perform(delete("/api/v1/problem-sessions/{problemSessionId}", problemSessionId)
                        .header("Authorization", "Bearer " + tokenFor(otherUserId)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PROBLEM_SESSION_NOT_FOUND"));

        assertThat(rowCount("problem_sessions", problemSessionId)).isEqualTo(1);
    }

    @Test
    void rollsBackProblemSessionDeletionWhenDerivedCleanupFailsAndAllowsRetry() throws Exception {
        var userId = insertUser("delete-retry@example.com");
        var problemSessionId = UUID.fromString("30000000-0000-4000-8000-000000000063");
        insertSolvedSession(userId, problemSessionId);
        jdbcTemplate.execute("alter table capture_events add constraint ck_capture_events_delete_retry_failure check (deleted_at is null)");
        insertCaptureEvent(userId, problemSessionId);

        mockMvc.perform(delete("/api/v1/problem-sessions/{problemSessionId}", problemSessionId)
                        .header("Authorization", "Bearer " + tokenFor(userId)))
                .andExpect(status().is5xxServerError());

        assertThat(rowCount("problem_sessions", problemSessionId)).isEqualTo(1);
        assertThat(rowCount("capture_events", problemSessionId)).isEqualTo(1);

        jdbcTemplate.execute("alter table capture_events drop constraint ck_capture_events_delete_retry_failure");

        mockMvc.perform(delete("/api/v1/problem-sessions/{problemSessionId}", problemSessionId)
                        .header("Authorization", "Bearer " + tokenFor(userId)))
                .andExpect(status().isNoContent());

        assertThat(rowCount("problem_sessions", problemSessionId)).isZero();
        assertThat(rowCount("capture_events", problemSessionId)).isZero();
    }

    private void startSession(UUID userId, UUID problemSessionId) throws Exception {
        mockMvc.perform(post("/api/v1/problem-sessions")
                        .header("Authorization", "Bearer " + tokenFor(userId))
                        .contentType("application/json")
                        .content(startJson(problemSessionId, null)))
                .andExpect(status().isOk());
    }

    private void finishSession(UUID userId, UUID problemSessionId, String ifMatch) throws Exception {
        mockMvc.perform(post("/api/v1/problem-sessions/{problemSessionId}/finish", problemSessionId)
                        .header("Authorization", "Bearer " + tokenFor(userId))
                        .header("If-Match", ifMatch)
                        .contentType("application/json")
                        .content(finishJson("2026-07-13T08:05:00Z")))
                .andExpect(status().isOk());
    }

    private void saveSolution(
            UUID userId, UUID problemSessionId, String ifMatch, String idempotencyKey, String finalSolution)
            throws Exception {
        mockMvc.perform(put("/api/v1/problem-sessions/{problemSessionId}/solution", problemSessionId)
                        .header("Authorization", "Bearer " + tokenFor(userId))
                        .header("If-Match", ifMatch)
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType("application/json")
                        .content(solutionJson(finalSolution)))
                .andExpect(status().isOk());
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

    private void insertSolvedSession(UUID userId, UUID problemSessionId) {
        jdbcTemplate.update(
                """
                insert into problem_sessions (
                    id,
                    user_id,
                    status,
                    source,
                    title,
                    final_solution,
                    final_solution_author,
                    solution_authored_at,
                    started_at,
                    ended_at,
                    search_document,
                    created_at,
                    updated_at
                ) values (?, ?, 'SOLVED', 'MANUAL', 'Solved problem', ?, 'USER', ?::timestamptz, ?::timestamptz, ?::timestamptz, 'Solved problem', ?::timestamptz, ?::timestamptz)
                """,
                problemSessionId,
                userId,
                "Allowed localhost:3000 in CorsConfiguration.",
                "2026-07-13T08:10:00Z",
                "2026-07-13T08:00:00Z",
                "2026-07-13T08:10:00Z",
                "2026-07-13T08:00:00Z",
                "2026-07-13T08:10:00Z");
    }

    private void insertReviewableSession(UUID userId, UUID problemSessionId, long version) {
        jdbcTemplate.update(
                """
                insert into problem_sessions (
                    id,
                    user_id,
                    status,
                    source,
                    version,
                    title,
                    started_at,
                    ended_at,
                    search_document,
                    created_at,
                    updated_at
                ) values (?, ?, 'REVIEWABLE', 'MANUAL', ?, 'Reviewable problem', ?::timestamptz, ?::timestamptz, 'Reviewable problem', ?::timestamptz, ?::timestamptz)
                """,
                problemSessionId,
                userId,
                version,
                "2026-07-13T08:00:00Z",
                "2026-07-13T08:30:00Z",
                "2026-07-13T08:00:00Z",
                "2026-07-13T08:30:00Z");
    }

    private void insertSessionArtifact(UUID userId, UUID problemSessionId) {
        jdbcTemplate.update(
                """
                insert into session_artifacts (
                    id,
                    user_id,
                    problem_session_id,
                    position,
                    artifact_type,
                    sanitized_url,
                    canonical_url_hash,
                    hostname,
                    title,
                    occurred_at
                ) values (?, ?, ?, 1, 'DOCUMENTATION', 'https://docs.spring.io/cors', decode(repeat('01', 32), 'hex'), 'docs.spring.io', 'Spring CORS docs', ?::timestamptz)
                """,
                UUID.randomUUID(),
                userId,
                problemSessionId,
                "2026-07-13T08:05:00Z");
    }

    private void insertCaptureEvent(UUID userId, UUID problemSessionId) {
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
                    policy_version,
                    occurred_at,
                    retention_expires_at,
                    candidate_problem_session_id
                ) values (?, ?, ?, 'extension-instance', 'WEB_NAVIGATION_COMMITTED', 'DOCUMENTATION', 'https://docs.spring.io/cors', decode(repeat('02', 32), 'hex'), 'docs.spring.io', 'url-query-sanitation-v1', ?::timestamptz, ?::timestamptz, ?)
                """,
                UUID.randomUUID(),
                userId,
                "delete-event-" + problemSessionId,
                "2026-07-13T08:05:00Z",
                "2026-07-20T08:05:00Z",
                problemSessionId);
    }

    private void insertBackgroundJob(UUID userId, UUID problemSessionId) {
        jdbcTemplate.update(
                """
                insert into background_jobs (
                    id,
                    user_id,
                    aggregate_type,
                    aggregate_id,
                    job_type,
                    state,
                    next_attempt_at
                ) values (?, ?, 'PROBLEM_SESSION', ?, 'PROBLEM_SESSION_ENRICHMENT', 'READY', ?::timestamptz)
                on conflict do nothing
                """,
                UUID.randomUUID(),
                userId,
                problemSessionId,
                "2026-07-13T08:05:00Z");
    }

    private void insertEmbeddingGeneration() {
        jdbcTemplate.update(
                """
                insert into embedding_model_generations (
                    id,
                    generation_key,
                    generation_version,
                    provider,
                    model,
                    dimensions,
                    active
                ) values (?, 'solved-session', 1, 'mock', 'trail-mock-embedding', 3, true)
                on conflict do nothing
                """,
                UUID.fromString("30000000-0000-4000-8000-000000000064"));
    }

    private void insertAIDerivation(UUID userId, UUID problemSessionId) {
        jdbcTemplate.update(
                """
                insert into ai_derivations (
                    id,
                    user_id,
                    problem_session_id,
                    derivation_type,
                    provider,
                    model,
                    prompt_key,
                    prompt_version,
                    prompt_hash,
                    output_schema_hash,
                    policy_version,
                    input_fingerprint,
                    status,
                    generated_summary,
                    completed_at
                ) values (?, ?, ?, 'PROBLEM_SESSION_ENRICHMENT', 'mock', 'trail-mock-enrichment', 'problem-session-enrichment', 1, repeat('a', 64), repeat('b', 64), 'ai-redaction-v1', repeat('c', 64), 'SUCCEEDED', 'Generated summary', ?::timestamptz)
                """,
                UUID.randomUUID(),
                userId,
                problemSessionId,
                "2026-07-13T08:06:00Z");
    }

    private void insertProblemEmbedding(UUID userId, UUID problemSessionId) {
        jdbcTemplate.update(
                """
                insert into problem_embeddings (
                    id,
                    user_id,
                    problem_session_id,
                    generation_key,
                    generation_version,
                    provider,
                    model,
                    dimensions,
                    input_fingerprint,
                    policy_version,
                    embedding
                ) values (?, ?, ?, 'solved-session', 1, 'mock', 'trail-mock-embedding', 3, repeat('d', 64), 'embedding-redaction-v1', '[0.1,0.2,0.3]'::vector)
                """,
                UUID.randomUUID(),
                userId,
                problemSessionId);
    }

    private void insertRecommendationEvent(UUID userId, UUID currentSessionId, UUID recommendedSessionId) {
        jdbcTemplate.update(
                """
                insert into recommendation_events (
                    id,
                    user_id,
                    recommendation_id,
                    current_problem_session_id,
                    recommended_problem_session_id,
                    outcome,
                    retrieved_problem_sessions,
                    evidence,
                    embedding_generation_key,
                    embedding_generation_version,
                    embedding_provider,
                    embedding_model,
                    embedding_input_fingerprint,
                    algorithm_version
                ) values (?, ?, ?, ?, ?, 'SHOWN', ?::jsonb, ?::jsonb, 'solved-session', 1, 'mock', 'trail-mock-embedding', repeat('e', 64), 'hybrid-similarity-v1')
                """,
                UUID.randomUUID(),
                userId,
                UUID.randomUUID(),
                currentSessionId,
                recommendedSessionId,
                "[{\"problemSessionId\":\"" + recommendedSessionId + "\",\"rank\":1}]",
                "[{\"evidenceCode\":\"ERROR_PHRASE\",\"problemSessionId\":\"" + recommendedSessionId + "\"}]");
    }

    private int problemSessionCount() {
        return jdbcTemplate.queryForObject("select count(*) from problem_sessions", Integer.class);
    }

    private int backgroundJobCount(UUID problemSessionId) {
        return jdbcTemplate.queryForObject(
                "select count(*) from background_jobs where aggregate_id = ?", Integer.class, problemSessionId);
    }

    private int rowCount(String tableName, UUID problemSessionId) {
        var columnName = switch (tableName) {
            case "problem_sessions" -> "id";
            case "background_jobs" -> "aggregate_id";
            case "capture_events" -> "candidate_problem_session_id";
            case "recommendation_events" -> "current_problem_session_id";
            default -> "problem_session_id";
        };
        return jdbcTemplate.queryForObject(
                "select count(*) from " + tableName + " where " + columnName + " = ?",
                Integer.class,
                problemSessionId);
    }

    private String problemSessionStatus(UUID problemSessionId) {
        return jdbcTemplate.queryForObject(
                "select status from problem_sessions where id = ?", String.class, problemSessionId);
    }

    private String storedFinalSolution(UUID problemSessionId) {
        return jdbcTemplate.queryForObject(
                "select final_solution from problem_sessions where id = ?", String.class, problemSessionId);
    }

    private String storedSolutionAuthor(UUID problemSessionId) {
        return jdbcTemplate.queryForObject(
                "select final_solution_author from problem_sessions where id = ?", String.class, problemSessionId);
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

    private static String startJson(UUID problemSessionId, String title) {
        return """
                {
                  "problemSessionId": "%s",
                  "title": %s,
                  "startedAt": "2026-07-13T08:00:00Z"
                }
                """
                .formatted(problemSessionId, title == null ? "null" : "\"" + title + "\"");
    }

    private static String finishJson(String finishedAt) {
        return """
                {
                  "finishedAt": "%s"
                }
                """
                .formatted(finishedAt);
    }

    private static String solutionJson(String finalSolution) {
        return """
                {
                  "finalSolution": "%s"
                }
                """
                .formatted(finalSolution.replace("\\", "\\\\").replace("\"", "\\\""));
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
