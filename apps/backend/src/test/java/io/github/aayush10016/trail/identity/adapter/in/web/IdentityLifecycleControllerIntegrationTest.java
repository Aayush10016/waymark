package io.github.aayush10016.trail.identity.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.aayush10016.trail.bootstrap.TrailApplication;
import io.github.aayush10016.trail.identity.adapter.out.email.LocalEmailDeliveryAdapter;
import io.github.aayush10016.trail.identity.domain.model.EmailDeliveryTemplate;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.regex.Pattern;
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
class IdentityLifecycleControllerIntegrationTest {

    private static final String POSTGRES_PGVECTOR_IMAGE =
            "pgvector/pgvector:0.8.2-pg17@sha256:feb68f4f15446397d8cac7f4fe48fe4586de83160d1fc48b46283312d1a33966";
    private static final Pattern TOKEN_URL_PATTERN = Pattern.compile("token=([A-Za-z0-9_-]{30,})");
    private static final KeyPair KEY_PAIR = generateKeyPair();

    @Container
    static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>(
                            DockerImageName.parse(POSTGRES_PGVECTOR_IMAGE)
                                    .asCompatibleSubstituteFor("postgres"))
                    .withDatabaseName("trail_identity_lifecycle_api_test")
                    .withUsername("trail")
                    .withPassword("trail_local_password");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private LocalEmailDeliveryAdapter emailDeliveryAdapter;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        registry.add("trail.database.url", postgres::getJdbcUrl);
        registry.add("trail.database.username", postgres::getUsername);
        registry.add("trail.database.password", postgres::getPassword);
        registry.add("trail.identity.jwt.key-id", () -> "test-key");
        registry.add("trail.identity.jwt.issuer", () -> "trail-test");
        registry.add("trail.identity.jwt.audience", () -> "trail-extension");
        registry.add("trail.identity.jwt.public-key-pem", IdentityLifecycleControllerIntegrationTest::publicKeyPem);
        registry.add("trail.identity.jwt.private-key-pem", IdentityLifecycleControllerIntegrationTest::privateKeyPem);
        registry.add("trail.identity.jwt.access-token-time-to-live", () -> "10m");
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
    void completesIdentityLifecycleAgainstPostgreSqlAndJwtVerifier() throws Exception {
        var email = "developer@example.com";

        mockMvc.perform(post("/api/v1/registrations")
                        .contentType("application/json")
                        .content("{\"email\":\"" + email + "\",\"password\":\"old secure password\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("ACCEPTED"));

        assertThat(userStatus(email)).isEqualTo("PENDING_VERIFICATION");
        assertThat(rawVerificationOrResetToken(EmailDeliveryTemplate.VERIFY_EMAIL)).isNotBlank();

        mockMvc.perform(post("/api/v1/auth-sessions")
                        .contentType("application/json")
                        .content("{\"email\":\"" + email + "\",\"password\":\"old secure password\"}"))
                .andExpect(status().isUnauthorized());

        var verificationToken = rawVerificationOrResetToken(EmailDeliveryTemplate.VERIFY_EMAIL);
        mockMvc.perform(post("/api/v1/email-verifications")
                        .contentType("application/json")
                        .content("{\"token\":\"" + verificationToken + "\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("ACCEPTED"));

        assertThat(userStatus(email)).isEqualTo("ACTIVE");

        var session = createAuthSession(email, "old secure password");
        assertThat(session.get("accessToken").asText()).isNotBlank();
        assertThat(session.get("refreshToken").asText()).isNotBlank();
        assertThat(rawRefreshTokenCount()).isZero();

        mockMvc.perform(delete("/api/v1/account")
                        .header("Authorization", "Bearer " + session.get("accessToken").asText())
                        .contentType("application/json")
                        .content("{\"password\":\"wrong password\"}"))
                .andExpect(status().isForbidden());

        var rotatedSession = refreshSession(session.get("refreshToken").asText());
        assertThat(rotatedSession.get("accessToken").asText()).isNotEqualTo(session.get("accessToken").asText());
        assertThat(rotatedSession.get("refreshToken").asText()).isNotEqualTo(session.get("refreshToken").asText());

        mockMvc.perform(post("/api/v1/auth-sessions/refresh")
                        .contentType("application/json")
                        .content("{\"refreshToken\":\"" + session.get("refreshToken").asText() + "\"}"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/password-reset-requests")
                        .contentType("application/json")
                        .content("{\"email\":\"" + email + "\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("ACCEPTED"));

        var resetToken = rawVerificationOrResetToken(EmailDeliveryTemplate.RESET_PASSWORD);
        mockMvc.perform(post("/api/v1/password-resets")
                        .contentType("application/json")
                        .content("{\"token\":\"" + resetToken + "\",\"newPassword\":\"new secure password\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("ACCEPTED"));

        assertThat(activeSessionCount()).isZero();
        mockMvc.perform(post("/api/v1/auth-sessions")
                        .contentType("application/json")
                        .content("{\"email\":\"" + email + "\",\"password\":\"old secure password\"}"))
                .andExpect(status().isUnauthorized());
        assertThat(createAuthSession(email, "new secure password").get("accessToken").asText()).isNotBlank();
    }

    @Test
    void keepsRegistrationAndRecoveryResponsesGeneric() throws Exception {
        mockMvc.perform(post("/api/v1/registrations")
                        .contentType("application/json")
                        .content("{\"email\":\"duplicate@example.com\",\"password\":\"secure password\"}"))
                .andExpect(status().isAccepted());

        mockMvc.perform(post("/api/v1/registrations")
                        .contentType("application/json")
                        .content("{\"email\":\"DUPLICATE@example.com\",\"password\":\"secure password\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("ACCEPTED"));

        mockMvc.perform(post("/api/v1/password-reset-requests")
                        .contentType("application/json")
                        .content("{\"email\":\"missing@example.com\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("ACCEPTED"));
    }

    @Test
    void rejectsMalformedRequestsWithoutSensitiveEcho() throws Exception {
        mockMvc.perform(post("/api/v1/registrations")
                        .contentType("application/json")
                        .content("{\"email\":\"invalid\",\"password\":\"secret\",\"extra\":\"field\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("IDENTITY_INVALID_REQUEST"))
                .andExpect(jsonPath("$.detail").value("Identity request is invalid."));
    }

    @Test
    void revokesCurrentRefreshSessionFamily() throws Exception {
        var email = "logout@example.com";
        mockMvc.perform(post("/api/v1/registrations")
                        .contentType("application/json")
                        .content("{\"email\":\"" + email + "\",\"password\":\"secure password\"}"))
                .andExpect(status().isAccepted());
        mockMvc.perform(post("/api/v1/email-verifications")
                        .contentType("application/json")
                        .content("{\"token\":\"" + rawVerificationOrResetToken(EmailDeliveryTemplate.VERIFY_EMAIL) + "\"}"))
                .andExpect(status().isAccepted());

        var session = createAuthSession(email, "secure password");
        mockMvc.perform(delete("/api/v1/auth-sessions/current")
                        .contentType("application/json")
                        .content("{\"refreshToken\":\"" + session.get("refreshToken").asText() + "\"}"))
                .andExpect(status().isNoContent());
        assertThat(activeSessionCount()).isZero();
    }

    private JsonNode createAuthSession(String email, String password) throws Exception {
        var response = mockMvc.perform(post("/api/v1/auth-sessions")
                        .contentType("application/json")
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.accessTokenExpiresAt").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshTokenExpiresAt").isNotEmpty())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(response);
    }

    private JsonNode refreshSession(String refreshToken) throws Exception {
        var response = mockMvc.perform(post("/api/v1/auth-sessions/refresh")
                        .contentType("application/json")
                        .content("{\"refreshToken\":\"" + refreshToken + "\"}"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(response);
    }

    private String rawVerificationOrResetToken(EmailDeliveryTemplate template) {
        return emailDeliveryAdapter.deliveredMessages().stream()
                .filter(message -> message.template() == template)
                .reduce((ignored, latest) -> latest)
                .map(message -> {
                    var matcher = TOKEN_URL_PATTERN.matcher(message.textBody());
                    if (!matcher.find()) {
                        throw new IllegalStateException("identity email does not contain a URL-carried token");
                    }
                    return matcher.group(1);
                })
                .orElseThrow();
    }

    private String userStatus(String email) {
        return jdbcTemplate.queryForObject(
                "select status from users where email_normalized = ?",
                String.class,
                email.toLowerCase(java.util.Locale.ROOT));
    }

    private int activeSessionCount() {
        return jdbcTemplate.queryForObject(
                "select count(*) from auth_sessions where revoked_at is null and reuse_detected_at is null",
                Integer.class);
    }

    private int rawRefreshTokenCount() {
        return jdbcTemplate.queryForObject(
                "select count(*) from auth_sessions where encode(refresh_token_hash, 'escape') like '%refresh%'",
                Integer.class);
    }

    private static String publicKeyPem() {
        return pemBoundary("PUBLIC", true)
                + "\n"
                + Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII))
                        .encodeToString(KEY_PAIR.getPublic().getEncoded())
                + "\n"
                + pemBoundary("PUBLIC", false);
    }

    private static String privateKeyPem() {
        return pemBoundary("PRIVATE", true)
                + "\n"
                + Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII))
                        .encodeToString(KEY_PAIR.getPrivate().getEncoded())
                + "\n"
                + pemBoundary("PRIVATE", false);
    }

    private static String pemBoundary(String keyType, boolean begin) {
        return "-----" + (begin ? "BEGIN" : "END") + " " + keyType + " KEY-----";
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
