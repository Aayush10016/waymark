package io.github.aayush10016.trail.identity.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.aayush10016.trail.bootstrap.TrailApplication;
import io.github.aayush10016.trail.identity.domain.model.AuthenticatedPrincipal;
import io.github.aayush10016.trail.identity.security.Argon2idPasswordPolicy;
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
class AccountDeletionControllerIntegrationTest {

    private static final String POSTGRES_PGVECTOR_IMAGE =
            "pgvector/pgvector:0.8.2-pg17@sha256:feb68f4f15446397d8cac7f4fe48fe4586de83160d1fc48b46283312d1a33966";
    private static final KeyPair KEY_PAIR = generateKeyPair();

    @Container
    static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>(
                            DockerImageName.parse(POSTGRES_PGVECTOR_IMAGE)
                                    .asCompatibleSubstituteFor("postgres"))
                    .withDatabaseName("trail_account_deletion_api_test")
                    .withUsername("trail")
                    .withPassword("trail_local_password");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final Argon2idPasswordPolicy passwordPolicy = new Argon2idPasswordPolicy();

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        registry.add("trail.database.url", postgres::getJdbcUrl);
        registry.add("trail.database.username", postgres::getUsername);
        registry.add("trail.database.password", postgres::getPassword);
        registry.add("trail.identity.jwt.key-id", () -> "test-key");
        registry.add("trail.identity.jwt.issuer", () -> "trail-test");
        registry.add("trail.identity.jwt.audience", () -> "trail-extension");
        registry.add("trail.identity.jwt.public-key-pem", AccountDeletionControllerIntegrationTest::publicKeyPem);
        registry.add("trail.background-jobs.scheduler.enabled", () -> "false");
    }

    @BeforeEach
    void migrateFromEmptyDatabase() {
        var flyway = Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration")
                .cleanDisabled(false)
                .load();
        flyway.clean();
        flyway.migrate();
    }

    @Test
    void reauthenticatesRevokesSessionsAndPurgesUserData() throws Exception {
        var userId = insertUser("delete-account@example.com", "correct-password");
        insertAuthSession(userId);
        insertProblemSession(userId);
        insertCaptureEvent(userId);
        insertConsentGrant(userId);

        mockMvc.perform(delete("/api/v1/account")
                        .header("Authorization", "Bearer " + tokenFor(userId))
                        .contentType("application/json")
                        .content("{\"password\":\"correct-password\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.localCleanupRequired").value(true))
                .andExpect(jsonPath("$.backupDisclosure").isNotEmpty());

        assertThat(userOwnedCount("users", userId)).isZero();
        assertThat(userOwnedCount("auth_sessions", userId)).isZero();
        assertThat(userOwnedCount("problem_sessions", userId)).isZero();
        assertThat(userOwnedCount("capture_events", userId)).isZero();
        assertThat(userOwnedCount("consent_grants", userId)).isZero();
    }

    @Test
    void rejectsWrongPasswordWithoutPurgingAccount() throws Exception {
        var userId = insertUser("delete-account-wrong-password@example.com", "correct-password");
        insertAuthSession(userId);

        mockMvc.perform(delete("/api/v1/account")
                        .header("Authorization", "Bearer " + tokenFor(userId))
                        .contentType("application/json")
                        .content("{\"password\":\"wrong-password\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCOUNT_REAUTHENTICATION_FAILED"));

        assertThat(userOwnedCount("users", userId)).isEqualTo(1);
        assertThat(activeAuthSessionCount(userId)).isEqualTo(1);
    }

    @Test
    void rejectsMissingAuthenticationAndMalformedBodies() throws Exception {
        mockMvc.perform(delete("/api/v1/account")
                        .contentType("application/json")
                        .content("{\"password\":\"irrelevant\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));

        var userId = insertUser("delete-account-bad-body@example.com", "correct-password");
        mockMvc.perform(delete("/api/v1/account")
                        .header("Authorization", "Bearer " + tokenFor(userId))
                        .contentType("application/json")
                .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ACCOUNT_DELETION_INVALID_REQUEST"));
    }

    @Test
    void rejectsUnknownAccountDeletionFields() throws Exception {
        var userId = insertUser("delete-account-unknown-field@example.com", "correct-password");

        mockMvc.perform(delete("/api/v1/account")
                        .header("Authorization", "Bearer " + tokenFor(userId))
                        .contentType("application/json")
                        .content(
                                """
                                {
                                  "password": "correct-password",
                                  "userId": "00000000-0000-4000-8000-000000000123"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ACCOUNT_DELETION_INVALID_REQUEST"));

        assertThat(userOwnedCount("users", userId)).isEqualTo(1);
    }

    private UUID insertUser(String email, String password) {
        var userId = UUID.randomUUID();
        var hash = passwordPolicy.hash(password);
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
                ) values (?, ?, ?, ?, ?, ?, ?, 'ACTIVE', now())
                """,
                userId,
                email,
                email,
                hash.encodedHash(),
                hash.algorithm(),
                hash.parameters(),
                hash.version());
        return userId;
    }

    private void insertAuthSession(UUID userId) {
        jdbcTemplate.update(
                """
                insert into auth_sessions (
                    id,
                    session_family_id,
                    user_id,
                    refresh_token_hash,
                    refresh_token_hash_algorithm,
                    client_type,
                    expires_at
                ) values (?, ?, ?, decode(repeat('11', 32), 'hex'), 'SHA-256', 'CHROME_EXTENSION', now() + interval '30 days')
                """,
                UUID.randomUUID(),
                UUID.randomUUID(),
                userId);
    }

    private void insertProblemSession(UUID userId) {
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
                    search_document
                ) values (?, ?, 'SOLVED', 'MANUAL', 'Deleted account session', 'Solved.', 'USER', now(), now(), now(), 'Deleted account session')
                """,
                UUID.randomUUID(),
                userId);
    }

    private void insertCaptureEvent(UUID userId) {
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
                    retention_expires_at
                ) values (?, ?, ?, 'extension-instance', 'WEB_NAVIGATION_COMMITTED', 'DOCUMENTATION', 'https://docs.spring.io/cors', decode(repeat('22', 32), 'hex'), 'docs.spring.io', 'url-query-sanitation-v1', now(), now() + interval '7 days')
                """,
                UUID.randomUUID(),
                userId,
                "account-delete-event-" + userId);
    }

    private void insertConsentGrant(UUID userId) {
        jdbcTemplate.update(
                """
                insert into consent_grants (
                    id,
                    user_id,
                    policy_version,
                    capture_scope,
                    ai_scope,
                    decision,
                    granted_at
                ) values (?, ?, 'capture-consent-v1', 'BROWSER_NAVIGATION_METADATA', 'PROBLEM_MEMORY', 'GRANTED', now())
                """,
                UUID.randomUUID(),
                userId);
    }

    private int userOwnedCount(String tableName, UUID userId) {
        var columnName = tableName.equals("users") ? "id" : "user_id";
        return jdbcTemplate.queryForObject(
                "select count(*) from " + tableName + " where " + columnName + " = ?",
                Integer.class,
                userId);
    }

    private int activeAuthSessionCount(UUID userId) {
        return jdbcTemplate.queryForObject(
                "select count(*) from auth_sessions where user_id = ? and revoked_at is null",
                Integer.class,
                userId);
    }

    private String tokenFor(UUID userId) {
        var issuer = new JwtAccessTokenIssuer(
                KEY_PAIR.getPrivate(),
                "test-key",
                "trail-test",
                "trail-extension",
                Duration.ofMinutes(10),
                Clock.systemUTC(),
                () -> UUID.fromString("87000000-0000-4000-8000-000000000002"));
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
