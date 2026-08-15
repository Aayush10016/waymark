package io.github.aayush10016.trail.capture.adapter.in.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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

@SpringBootTest(
        classes = TrailApplication.class,
        properties = {
            "trail.features.ai-suggestions=true",
            "trail.features.ghost-replay=true",
            "trail.features.semantic-search=true"
        })
@AutoConfigureMockMvc
@Testcontainers
class CaptureStateControllerIntegrationTest {

    private static final String POSTGRES_PGVECTOR_IMAGE =
            "pgvector/pgvector:0.8.2-pg17@sha256:feb68f4f15446397d8cac7f4fe48fe4586de83160d1fc48b46283312d1a33966";
    private static final KeyPair KEY_PAIR = generateKeyPair();
    private static final UUID CURRENT_SESSION_ID = UUID.fromString("75100000-0000-4000-8000-000000000001");

    @Container
    static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>(
                            DockerImageName.parse(POSTGRES_PGVECTOR_IMAGE)
                                    .asCompatibleSubstituteFor("postgres"))
                    .withDatabaseName("trail_capture_state_api_test")
                    .withUsername("trail")
                    .withPassword("trail_local_password");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UUID userId;

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        registry.add("trail.database.url", postgres::getJdbcUrl);
        registry.add("trail.database.username", postgres::getUsername);
        registry.add("trail.database.password", postgres::getPassword);
        registry.add("trail.identity.jwt.key-id", () -> "test-key");
        registry.add("trail.identity.jwt.issuer", () -> "trail-test");
        registry.add("trail.identity.jwt.audience", () -> "trail-extension");
        registry.add("trail.identity.jwt.public-key-pem", CaptureStateControllerIntegrationTest::publicKeyPem);
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
        userId = insertUser("capture-state@example.com");
    }

    @Test
    void returnsPromptEligibleCurrentCandidateAndPolicyFreshness() throws Exception {
        insertConsent(userId, "GRANTED");
        insertCurrentCandidate(userId);

        mockMvc.perform(get("/api/v1/capture-state")
                        .header("Authorization", "Bearer " + tokenFor(userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentCandidate.problemSessionId").value(CURRENT_SESSION_ID.toString()))
                .andExpect(jsonPath("$.promptEligibility.status").value("ELIGIBLE"))
                .andExpect(jsonPath("$.recommendation.currentProblemSessionId").value(CURRENT_SESSION_ID.toString()))
                .andExpect(jsonPath("$.recommendation.status").value("DETAIL_ENDPOINT_REQUIRED"))
                .andExpect(jsonPath("$.policyFreshness.fresh").value(true));
    }

    @Test
    void returnsConsentRequiredWhenConsentIsMissing() throws Exception {
        mockMvc.perform(get("/api/v1/capture-state")
                        .header("Authorization", "Bearer " + tokenFor(userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentCandidate").doesNotExist())
                .andExpect(jsonPath("$.promptEligibility.reason").value("CONSENT_REQUIRED"))
                .andExpect(jsonPath("$.recommendation.status").value("NO_CURRENT_CANDIDATE"))
                .andExpect(jsonPath("$.policyFreshness.fresh").value(false));
    }

    @Test
    void rejectsMissingAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/capture-state"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
    }

    private UUID insertUser(String email) {
        var insertedUserId = UUID.randomUUID();
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
                insertedUserId,
                email,
                email,
                "$argon2id$v=19$m=65536,t=3,p=2$synthetic$safehash",
                "m=65536,t=3,p=2");
        return insertedUserId;
    }

    private void insertConsent(UUID consentUserId, String decision) {
        jdbcTemplate.update(
                """
                insert into consent_grants (
                    id,
                    user_id,
                    policy_version,
                    capture_scope,
                    ai_scope,
                    decision,
                    granted_at,
                    withdrawn_at
                ) values (?, ?, 'capture-consent-v1', 'BROWSER_NAVIGATION_METADATA', 'NONE', ?, ?::timestamptz, ?::timestamptz)
                """,
                UUID.randomUUID(),
                consentUserId,
                decision,
                "GRANTED".equals(decision) ? "2026-07-13T08:00:00Z" : null,
                "WITHDRAWN".equals(decision) ? "2026-07-13T09:00:00Z" : null);
    }

    private void insertCurrentCandidate(UUID candidateUserId) {
        jdbcTemplate.update(
                """
                insert into problem_sessions (
                    id,
                    user_id,
                    status,
                    source,
                    title,
                    search_document,
                    started_at,
                    created_at,
                    updated_at
                ) values (?, ?, 'CANDIDATE', 'MANUAL', 'Spring CORS', 'Spring CORS', ?::timestamptz, ?::timestamptz, ?::timestamptz)
                """,
                CURRENT_SESSION_ID,
                candidateUserId,
                "2026-07-13T08:00:00Z",
                "2026-07-13T08:00:00Z",
                "2026-07-13T08:00:00Z");
    }

    private String tokenFor(UUID tokenUserId) {
        var issuer = new JwtAccessTokenIssuer(
                KEY_PAIR.getPrivate(),
                "test-key",
                "trail-test",
                "trail-extension",
                Duration.ofMinutes(10),
                Clock.systemUTC(),
                () -> UUID.fromString("00000000-0000-4000-8000-000000000001"));
        return issuer.issue(new AuthenticatedPrincipal(tokenUserId)).token();
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
