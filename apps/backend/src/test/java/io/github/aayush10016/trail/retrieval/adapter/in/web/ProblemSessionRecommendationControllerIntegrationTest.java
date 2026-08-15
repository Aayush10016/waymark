package io.github.aayush10016.trail.retrieval.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import java.util.Base64;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest(
        classes = TrailApplication.class,
        properties = {
            "trail.features.ai-suggestions=true",
            "trail.features.ghost-replay=true",
            "trail.features.semantic-search=true"
        })
@AutoConfigureMockMvc
@Testcontainers
class ProblemSessionRecommendationControllerIntegrationTest {

    private static final String POSTGRES_PGVECTOR_IMAGE =
            "pgvector/pgvector:0.8.2-pg17@sha256:feb68f4f15446397d8cac7f4fe48fe4586de83160d1fc48b46283312d1a33966";
    private static final KeyPair KEY_PAIR = generateKeyPair();
    private static final UUID CURRENT_SESSION_ID = UUID.fromString("75000000-0000-4000-8000-000000000001");
    private static final UUID SOLVED_SESSION_ID = UUID.fromString("75000000-0000-4000-8000-000000000002");
    private static final UUID NO_EMBEDDING_SESSION_ID = UUID.fromString("75000000-0000-4000-8000-000000000003");

    @Container
    static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>(
                            DockerImageName.parse(POSTGRES_PGVECTOR_IMAGE)
                                    .asCompatibleSubstituteFor("postgres"))
                    .withDatabaseName("trail_recommendation_api_test")
                    .withUsername("trail")
                    .withPassword("trail_local_password");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UUID ownerId;
    private UUID otherUserId;

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        registry.add("trail.database.url", postgres::getJdbcUrl);
        registry.add("trail.database.username", postgres::getUsername);
        registry.add("trail.database.password", postgres::getPassword);
        registry.add("trail.identity.jwt.key-id", () -> "test-key");
        registry.add("trail.identity.jwt.issuer", () -> "trail-test");
        registry.add("trail.identity.jwt.audience", () -> "trail-extension");
        registry.add("trail.identity.jwt.public-key-pem", ProblemSessionRecommendationControllerIntegrationTest::publicKeyPem);
        registry.add("trail.background-jobs.scheduler.enabled", () -> "false");
    }

    @BeforeEach
    void migrateAndSeed() {
        var flyway = Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration")
                .cleanDisabled(false)
                .load();
        flyway.clean();
        flyway.migrate();

        ownerId = insertUser("recommendation-owner@example.com");
        otherUserId = insertUser("recommendation-other@example.com");
        insertCurrentCandidate(ownerId, CURRENT_SESSION_ID, true);
        insertCurrentCandidate(ownerId, NO_EMBEDDING_SESSION_ID, false);
        insertSolvedSession(ownerId, SOLVED_SESSION_ID);
        insertSolvedSession(otherUserId, UUID.fromString("75000000-0000-4000-8000-000000000004"));
    }

    @Test
    void returnsBoundedRecommendationDetailWithEtagAndNoRawScores() throws Exception {
        var response = mockMvc.perform(get("/api/v1/problem-sessions/{id}/recommendation", CURRENT_SESSION_ID)
                        .header("Authorization", "Bearer " + tokenFor(ownerId)))
                .andExpect(status().isOk())
                .andExpect(header().exists("ETag"))
                .andExpect(jsonPath("$.currentProblemSessionId").value(CURRENT_SESSION_ID.toString()))
                .andExpect(jsonPath("$.recommendedProblemSessionId").value(SOLVED_SESSION_ID.toString()))
                .andExpect(jsonPath("$.strength").value("STRONG"))
                .andExpect(jsonPath("$.finalSolution").value("Allowed localhost in CorsConfiguration and registered the JWT filter before UsernamePasswordAuthenticationFilter."))
                .andExpect(jsonPath("$.durationSeconds").value(3600))
                .andExpect(jsonPath("$.evidence[0].evidenceCode").value("ERROR_PHRASE"))
                .andExpect(jsonPath("$.whyThis[0].message").isNotEmpty())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(response).doesNotContain("hybridScore").doesNotContain("vectorDistance").doesNotContain("lexicalScore");
        assertThat(recommendationEventCount("SHOWN")).isEqualTo(1);

        mockMvc.perform(get("/api/v1/problem-sessions/{id}/recommendation", CURRENT_SESSION_ID)
                        .header("Authorization", "Bearer " + tokenFor(ownerId)))
                .andExpect(status().isOk());
        assertThat(recommendationEventCount("SHOWN")).isEqualTo(1);
    }

    @Test
    void recordsAllowlistedClientRecommendationEventsIdempotentlyAfterShownEvent() throws Exception {
        mockMvc.perform(get("/api/v1/problem-sessions/{id}/recommendation", CURRENT_SESSION_ID)
                        .header("Authorization", "Bearer " + tokenFor(ownerId)))
                .andExpect(status().isOk());
        var recommendationId = shownRecommendationId();

        mockMvc.perform(post("/api/v1/recommendations/{id}/events", recommendationId)
                        .header("Authorization", "Bearer " + tokenFor(ownerId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"outcome\":\"OPENED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recommendationId").value(recommendationId.toString()))
                .andExpect(jsonPath("$.outcome").value("OPENED"))
                .andExpect(jsonPath("$.status").value("ACCEPTED"));

        mockMvc.perform(post("/api/v1/recommendations/{id}/events", recommendationId)
                        .header("Authorization", "Bearer " + tokenFor(ownerId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"outcome\":\"OPENED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DUPLICATE"));

        assertThat(recommendationEventCount("OPENED")).isEqualTo(1);
    }

    @Test
    void recallFeedbackFlowAcceptsHelpfulAndSuppressesNotRelatedRecommendations() throws Exception {
        mockMvc.perform(get("/api/v1/problem-sessions/{id}/recommendation", CURRENT_SESSION_ID)
                        .header("Authorization", "Bearer " + tokenFor(ownerId)))
                .andExpect(status().isOk());
        var recommendationId = shownRecommendationId();

        mockMvc.perform(post("/api/v1/recommendations/{id}/events", recommendationId)
                        .header("Authorization", "Bearer " + tokenFor(ownerId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"outcome\":\"HELPFUL\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("HELPFUL"))
                .andExpect(jsonPath("$.status").value("ACCEPTED"));

        mockMvc.perform(post("/api/v1/recommendations/{id}/events", recommendationId)
                        .header("Authorization", "Bearer " + tokenFor(ownerId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"outcome\":\"NOT_RELATED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("NOT_RELATED"))
                .andExpect(jsonPath("$.status").value("ACCEPTED"));

        mockMvc.perform(get("/api/v1/problem-sessions/{id}/recommendation", CURRENT_SESSION_ID)
                        .header("Authorization", "Bearer " + tokenFor(ownerId)))
                .andExpect(status().isNoContent());

        assertThat(recommendationEventCount("HELPFUL")).isEqualTo(1);
        assertThat(recommendationEventCount("NOT_RELATED")).isEqualTo(1);
    }

    @Test
    void rejectsClientSubmittedShownAndCrossUserEvents() throws Exception {
        mockMvc.perform(get("/api/v1/problem-sessions/{id}/recommendation", CURRENT_SESSION_ID)
                        .header("Authorization", "Bearer " + tokenFor(ownerId)))
                .andExpect(status().isOk());
        var recommendationId = shownRecommendationId();

        mockMvc.perform(post("/api/v1/recommendations/{id}/events", recommendationId)
                        .header("Authorization", "Bearer " + tokenFor(ownerId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"outcome\":\"SHOWN\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SEARCH_INVALID_REQUEST"));

        mockMvc.perform(post("/api/v1/recommendations/{id}/events", recommendationId)
                        .header("Authorization", "Bearer " + tokenFor(otherUserId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"outcome\":\"HELPFUL\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RECOMMENDATION_TARGET_NOT_FOUND"));
    }

    @Test
    void rejectsUnknownRecommendationEventFields() throws Exception {
        mockMvc.perform(get("/api/v1/problem-sessions/{id}/recommendation", CURRENT_SESSION_ID)
                        .header("Authorization", "Bearer " + tokenFor(ownerId)))
                .andExpect(status().isOk());
        var recommendationId = shownRecommendationId();

        mockMvc.perform(post("/api/v1/recommendations/{id}/events", recommendationId)
                        .header("Authorization", "Bearer " + tokenFor(ownerId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                {
                                  "outcome": "HELPFUL",
                                  "recommendedProblemSessionId": "%s",
                                  "analytics": {"clicked": true}
                                }
                                """.formatted(SOLVED_SESSION_ID)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SEARCH_INVALID_REQUEST"));
    }

    @Test
    void returnsNoContentWhenCurrentCandidateHasNoRecommendationInput() throws Exception {
        mockMvc.perform(get("/api/v1/problem-sessions/{id}/recommendation", NO_EMBEDDING_SESSION_ID)
                        .header("Authorization", "Bearer " + tokenFor(ownerId)))
                .andExpect(status().isNoContent());
    }

    @Test
    void enforcesAuthenticationAndOwnership() throws Exception {
        mockMvc.perform(get("/api/v1/problem-sessions/{id}/recommendation", CURRENT_SESSION_ID))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));

        mockMvc.perform(get("/api/v1/problem-sessions/{id}/recommendation", CURRENT_SESSION_ID)
                        .header("Authorization", "Bearer " + tokenFor(otherUserId)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RECOMMENDATION_TARGET_NOT_FOUND"));
    }

    private long recommendationEventCount(String outcome) {
        return jdbcTemplate.queryForObject(
                "select count(*) from recommendation_events where user_id = ? and outcome = ?",
                Long.class,
                ownerId,
                outcome);
    }

    private UUID shownRecommendationId() {
        return jdbcTemplate.queryForObject(
                "select recommendation_id from recommendation_events where user_id = ? and outcome = 'SHOWN'",
                UUID.class,
                ownerId);
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

    private void insertCurrentCandidate(UUID userId, UUID problemSessionId, boolean withEmbedding) {
        jdbcTemplate.update(
                """
                insert into problem_sessions (
                    id,
                    user_id,
                    status,
                    source,
                    title,
                    description,
                    technologies,
                    error_phrases,
                    search_document,
                    started_at,
                    created_at,
                    updated_at
                ) values (?, ?, 'CANDIDATE', 'MANUAL', ?, ?, ?, ?, ?, ?::timestamptz, ?::timestamptz, ?::timestamptz)
                """,
                problemSessionId,
                userId,
                "Spring Security CORS not working",
                "Browser preflight from localhost fails when JWT auth is enabled.",
                new String[] {"spring security", "jwt"},
                new String[] {"cors"},
                "Spring Security CORS not working JWT CorsConfiguration UsernamePasswordAuthenticationFilter",
                "2026-07-13T08:00:00Z",
                "2026-07-13T08:00:00Z",
                "2026-07-13T08:00:00Z");
        if (withEmbedding) {
            insertEmbedding(userId, problemSessionId, "[0.1,0.2,0.3]");
        }
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
                    duration_seconds,
                    technologies,
                    error_phrases,
                    problem_type,
                    search_document,
                    created_at,
                    updated_at
                ) values (
                    ?, ?, 'SOLVED', 'MANUAL', ?, ?, 'USER',
                    '2026-07-12T09:00:00Z'::timestamptz,
                    '2026-07-12T08:00:00Z'::timestamptz,
                    '2026-07-12T09:00:00Z'::timestamptz,
                    3600,
                    ?, ?, 'CONFIGURATION', ?,
                    '2026-07-12T08:00:00Z'::timestamptz,
                    '2026-07-12T09:00:00Z'::timestamptz
                )
                """,
                problemSessionId,
                userId,
                "Spring Security CORS blocked by JWT filter order",
                "Allowed localhost in CorsConfiguration and registered the JWT filter before UsernamePasswordAuthenticationFilter.",
                new String[] {"spring security", "jwt"},
                new String[] {"cors"},
                "Spring Security CORS CorsConfiguration JWT UsernamePasswordAuthenticationFilter allowed localhost");
        insertEmbedding(userId, problemSessionId, "[0.11,0.21,0.31]");
    }

    private void insertEmbedding(UUID userId, UUID problemSessionId, String vector) {
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
                ) values (?, ?, ?, 'solved-session', 1, 'mock', 'trail-mock-embedding', 3, ?, 'ai-redaction-v1', ?::vector)
                """,
                UUID.randomUUID(),
                userId,
                problemSessionId,
                "9".repeat(64),
                vector);
    }

    private String tokenFor(UUID userId) {
        var issuer = new JwtAccessTokenIssuer(
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
