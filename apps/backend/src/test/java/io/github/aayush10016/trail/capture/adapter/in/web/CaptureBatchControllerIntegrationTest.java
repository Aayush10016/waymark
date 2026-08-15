package io.github.aayush10016.trail.capture.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
class CaptureBatchControllerIntegrationTest {

    private static final String POSTGRES_PGVECTOR_IMAGE =
            "pgvector/pgvector:0.8.2-pg17@sha256:feb68f4f15446397d8cac7f4fe48fe4586de83160d1fc48b46283312d1a33966";
    private static final Instant NOW = Instant.parse("2026-07-13T08:00:00Z");
    private static final KeyPair KEY_PAIR = generateKeyPair();

    @Container
    static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>(
                            DockerImageName.parse(POSTGRES_PGVECTOR_IMAGE)
                                    .asCompatibleSubstituteFor("postgres"))
                    .withDatabaseName("trail_capture_api_test")
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
        registry.add("trail.identity.jwt.public-key-pem", CaptureBatchControllerIntegrationTest::publicKeyPem);
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
    void rejectsMissingAuthentication() throws Exception {
        mockMvc.perform(post("/api/v1/capture-batches")
                        .contentType("application/json")
                        .content(validBatchJson("event-1")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
    }

    @Test
    void rejectsEventsWhenConsentIsMissingWithoutPersisting() throws Exception {
        var userId = insertUser("missing-consent@example.com");

        mockMvc.perform(post("/api/v1/capture-batches")
                        .header("Authorization", "Bearer " + tokenFor(userId))
                        .contentType("application/json")
                        .content(validBatchJson("event-1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accepted.length()").value(0))
                .andExpect(jsonPath("$.rejected[0].code").value("CONSENT_REQUIRED"));

        assertThat(captureEventCount()).isZero();
    }

    @Test
    void recordsConsentGrantAndThenAcceptsCaptureBatch() throws Exception {
        var userId = insertUser("consent-endpoint@example.com");
        var accessToken = tokenFor(userId);

        mockMvc.perform(post("/api/v1/consent-grants")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType("application/json")
                        .content(
                                """
                                {
                                  "policyVersion": "capture-consent-v1",
                                  "decidedAt": "2026-07-13T08:00:00Z"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("GRANTED"))
                .andExpect(jsonPath("$.policyVersion").value("capture-consent-v1"));

        mockMvc.perform(post("/api/v1/capture-batches")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType("application/json")
                        .content(validBatchJson("event-after-consent")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accepted[0].clientEventId").value("event-after-consent"))
                .andExpect(jsonPath("$.rejected.length()").value(0));

        assertThat(captureEventCount()).isEqualTo(1);
    }

    @Test
    void recordsConsentWithdrawalAndThenRejectsCaptureBatch() throws Exception {
        var userId = insertUser("consent-withdrawal-endpoint@example.com");
        var accessToken = tokenFor(userId);
        insertConsent(userId, "GRANTED");

        mockMvc.perform(post("/api/v1/consent-withdrawals")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType("application/json")
                        .content(
                                """
                                {
                                  "policyVersion": "capture-consent-v1",
                                  "decidedAt": "2026-07-13T09:00:00Z"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("WITHDRAWN"));

        mockMvc.perform(post("/api/v1/capture-batches")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType("application/json")
                        .content(validBatchJson("event-after-withdrawal")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accepted.length()").value(0))
                .andExpect(jsonPath("$.rejected[0].code").value("CONSENT_REQUIRED"));

        assertThat(captureEventCount()).isZero();
    }

    @Test
    void rejectsUnsupportedConsentPolicyVersion() throws Exception {
        var userId = insertUser("unsupported-consent-policy@example.com");

        mockMvc.perform(post("/api/v1/consent-grants")
                        .header("Authorization", "Bearer " + tokenFor(userId))
                        .contentType("application/json")
                        .content(
                                """
                                {
                                  "policyVersion": "capture-consent-v0",
                                  "decidedAt": "2026-07-13T08:00:00Z"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CAPTURE_CONSENT_INVALID_REQUEST"));
    }

    @Test
    void acceptsValidEventsAndReturnsDuplicatesIdempotently() throws Exception {
        var userId = insertUser("accepted@example.com");
        insertConsent(userId, "GRANTED");

        mockMvc.perform(post("/api/v1/capture-batches")
                        .header("Authorization", "Bearer " + tokenFor(userId))
                        .contentType("application/json")
                        .content(validBatchJson("event-1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accepted[0].clientEventId").value("event-1"))
                .andExpect(jsonPath("$.duplicate.length()").value(0))
                .andExpect(jsonPath("$.rejected.length()").value(0));

        mockMvc.perform(post("/api/v1/capture-batches")
                        .header("Authorization", "Bearer " + tokenFor(userId))
                        .contentType("application/json")
                        .content(validBatchJson("event-1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accepted.length()").value(0))
                .andExpect(jsonPath("$.duplicate[0].clientEventId").value("event-1"));

        assertThat(captureEventCount()).isEqualTo(1);
    }

    @Test
    void returnsPartialValidationResults() throws Exception {
        var userId = insertUser("partial@example.com");
        insertConsent(userId, "GRANTED");

        mockMvc.perform(post("/api/v1/capture-batches")
                        .header("Authorization", "Bearer " + tokenFor(userId))
                        .contentType("application/json")
                        .content(
                                """
                                {
                                  "clientInstanceId": "extension-instance-1",
                                  "events": [
                                    {
                                      "clientEventId": "event-1",
                                      "clientSequence": 1,
                                      "source": "WEB_NAVIGATION_COMMITTED",
                                      "resourceType": "DOCUMENTATION",
                                      "url": "https://example.test/docs?utm_source=fixture&topic=cors",
                                      "title": "Example docs",
                                      "occurredAt": "2026-07-13T08:00:00Z"
                                    },
                                    {
                                      "clientEventId": "event-2",
                                      "clientSequence": 2,
                                      "source": "WEB_NAVIGATION_COMMITTED",
                                      "resourceType": "DOCUMENTATION",
                                      "url": "chrome://extensions",
                                      "title": "Extensions",
                                      "occurredAt": "2026-07-13T08:00:01Z"
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accepted[0].clientEventId").value("event-1"))
                .andExpect(jsonPath("$.rejected[0].clientEventId").value("event-2"))
                .andExpect(jsonPath("$.rejected[0].code").value("UNSAFE_URL"));

        assertThat(captureEventCount()).isEqualTo(1);
    }

    @Test
    void returnsStructuredValidationFailuresForInvalidEventMetadata() throws Exception {
        var userId = insertUser("invalid-metadata@example.com");
        insertConsent(userId, "GRANTED");

        mockMvc.perform(post("/api/v1/capture-batches")
                        .header("Authorization", "Bearer " + tokenFor(userId))
                        .contentType("application/json")
                        .content(
                                """
                                {
                                  "clientInstanceId": "",
                                  "events": [
                                    {
                                      "clientEventId": "event-1",
                                      "clientSequence": 1,
                                      "resourceType": "DOCUMENTATION",
                                      "url": "https://example.test/docs",
                                      "title": "Example docs"
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accepted.length()").value(0))
                .andExpect(jsonPath("$.duplicate.length()").value(0))
                .andExpect(jsonPath("$.rejected[0].clientEventId").value("event-1"))
                .andExpect(jsonPath("$.rejected[0].code").value("INVALID_CLIENT_INSTANCE_ID"));

        assertThat(captureEventCount()).isZero();
    }

    @Test
    void rejectsOversizedBatches() throws Exception {
        var userId = insertUser("oversized@example.com");
        insertConsent(userId, "GRANTED");

        mockMvc.perform(post("/api/v1/capture-batches")
                        .header("Authorization", "Bearer " + tokenFor(userId))
                        .contentType("application/json")
                        .content(oversizedBatchJson()))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.code").value("CAPTURE_BATCH_TOO_LARGE"));
    }

    @Test
    void rejectsUnknownBatchAndEventFields() throws Exception {
        var userId = insertUser("capture-mass-assignment@example.com");
        insertConsent(userId, "GRANTED");

        mockMvc.perform(post("/api/v1/capture-batches")
                        .header("Authorization", "Bearer " + tokenFor(userId))
                        .contentType("application/json")
                        .content(
                                """
                                {
                                  "clientInstanceId": "extension-instance-1",
                                  "userId": "00000000-0000-4000-8000-000000000123",
                                  "events": [
                                    {
                                      "clientEventId": "event-1",
                                      "clientSequence": 1,
                                      "source": "WEB_NAVIGATION_COMMITTED",
                                      "resourceType": "DOCUMENTATION",
                                      "url": "https://example.test/docs",
                                      "occurredAt": "2026-07-13T08:00:00Z"
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CAPTURE_INVALID_REQUEST"));

        mockMvc.perform(post("/api/v1/capture-batches")
                        .header("Authorization", "Bearer " + tokenFor(userId))
                        .contentType("application/json")
                        .content(
                                """
                                {
                                  "clientInstanceId": "extension-instance-1",
                                  "events": [
                                    {
                                      "clientEventId": "event-1",
                                      "clientSequence": 1,
                                      "source": "WEB_NAVIGATION_COMMITTED",
                                      "resourceType": "DOCUMENTATION",
                                      "url": "https://example.test/docs",
                                      "occurredAt": "2026-07-13T08:00:00Z",
                                      "rawUrl": "https://example.test/docs?token=secret"
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CAPTURE_INVALID_REQUEST"));

        assertThat(captureEventCount()).isZero();
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

    private void insertConsent(UUID userId, String decision) {
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
                userId,
                decision,
                "GRANTED".equals(decision) ? "2026-07-13T08:00:00Z" : null,
                "WITHDRAWN".equals(decision) ? "2026-07-13T09:00:00Z" : null);
    }

    private int captureEventCount() {
        return jdbcTemplate.queryForObject("select count(*) from capture_events", Integer.class);
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

    private String validBatchJson(String clientEventId) {
        return """
                {
                  "clientInstanceId": "extension-instance-1",
                  "events": [
                    {
                      "clientEventId": "%s",
                      "clientSequence": 1,
                      "tabSessionId": "tab-session-1",
                      "source": "WEB_NAVIGATION_COMMITTED",
                      "resourceType": "DOCUMENTATION",
                      "navigationType": "link",
                      "url": "https://example.test/docs?utm_source=fixture&topic=cors",
                      "title": "Example docs",
                      "browserEventTimeMilliseconds": 100,
                      "occurredAt": "2026-07-13T08:00:00Z",
                      "transitionQualifiers": ["from_address_bar"]
                    }
                  ]
                }
                """
                .formatted(clientEventId);
    }

    private String oversizedBatchJson() {
        var builder = new StringBuilder();
        builder.append("{\"clientInstanceId\":\"extension-instance-1\",\"events\":[");
        for (var index = 0; index < 51; index += 1) {
            if (index > 0) {
                builder.append(',');
            }
            builder.append(
                    """
                    {"clientEventId":"event-%d","source":"WEB_NAVIGATION_COMMITTED","resourceType":"DOCUMENTATION","url":"https://example.test/docs","occurredAt":"2026-07-13T08:00:00Z"}
                    """
                            .formatted(index));
        }
        builder.append("]}");
        return builder.toString();
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
