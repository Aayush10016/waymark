package io.github.aayush10016.trail.retrieval.adapter.in.web;

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
import java.sql.Connection;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest(classes = TrailApplication.class)
@AutoConfigureMockMvc
@Testcontainers
class ProblemSessionSearchControllerIntegrationTest {

    private static final String POSTGRES_PGVECTOR_IMAGE =
            "pgvector/pgvector:0.8.2-pg17@sha256:feb68f4f15446397d8cac7f4fe48fe4586de83160d1fc48b46283312d1a33966";
    private static final KeyPair KEY_PAIR = generateKeyPair();

    @Container
    static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>(
                            DockerImageName.parse(POSTGRES_PGVECTOR_IMAGE)
                                    .asCompatibleSubstituteFor("postgres"))
                    .withDatabaseName("trail_search_api_test")
                    .withUsername("trail")
                    .withPassword("trail_local_password");

    @Autowired
    private MockMvc mockMvc;

    private JdbcTemplate jdbcTemplate;
    private UUID ownerId;
    private UUID corsSessionId;
    private UUID csrfSessionId;
    private UUID reactSessionId;

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        registry.add("trail.database.url", postgres::getJdbcUrl);
        registry.add("trail.database.username", postgres::getUsername);
        registry.add("trail.database.password", postgres::getPassword);
        registry.add("trail.identity.jwt.key-id", () -> "test-key");
        registry.add("trail.identity.jwt.issuer", () -> "trail-test");
        registry.add("trail.identity.jwt.audience", () -> "trail-extension");
        registry.add("trail.identity.jwt.public-key-pem", ProblemSessionSearchControllerIntegrationTest::publicKeyPem);
        registry.add("trail.background-jobs.scheduler.enabled", () -> "false");
    }

    @BeforeEach
    void migrateAndSeed() throws Exception {
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

        try (var connection = dataSource.getConnection()) {
            ownerId = insertUser(connection, "search-owner-api@example.com");
            var otherUserId = insertUser(connection, "search-other-api@example.com");
            corsSessionId = UUID.fromString("30000000-0000-4000-8000-000000000067");
            csrfSessionId = UUID.fromString("30000000-0000-4000-8000-000000000068");
            reactSessionId = UUID.fromString("30000000-0000-4000-8000-000000000069");
            insertSolvedSession(
                    connection,
                    ownerId,
                    corsSessionId,
                    "Spring Security CORS not working",
                    "Allowed localhost in CorsConfiguration and placed the JWT filter before UsernamePasswordAuthenticationFilter.",
                    new String[] {"spring security", "jwt"},
                    new String[] {"cors"},
                    "CONFIGURATION",
                    "Spring Security CORS not working CorsConfiguration JWT UsernamePasswordAuthenticationFilter",
                    "2026-07-13T09:00:00Z");
            insertSolvedSession(
                    connection,
                    ownerId,
                    csrfSessionId,
                    "Spring Security CSRF token rejected",
                    "Disabled CSRF for stateless API endpoints.",
                    new String[] {"spring security"},
                    new String[] {"403"},
                    "CONFIGURATION",
                    "Spring Security CSRF token rejected stateless API 403",
                    "2026-07-12T09:00:00Z");
            insertSolvedSession(
                    connection,
                    ownerId,
                    reactSessionId,
                    "React hydration mismatch",
                    "Moved date formatting to a client effect.",
                    new String[] {"react"},
                    new String[] {"hydration mismatch"},
                    "FRONTEND",
                    "React hydration mismatch client effect",
                    "2026-07-11T09:00:00Z");
            insertSolvedSession(
                    connection,
                    otherUserId,
                    UUID.fromString("30000000-0000-4000-8000-000000000070"),
                    "Spring Security CORS other user",
                    "Other user solution must never be returned.",
                    new String[] {"spring security"},
                    new String[] {"cors"},
                    "CONFIGURATION",
                    "Spring Security CORS other user",
                    "2026-07-14T09:00:00Z");
        }
    }

    @Test
    void searchesUsingPostBodyAndReturnsLexicalDegradationEvidence() throws Exception {
        mockMvc.perform(post("/api/v1/problem-session-searches")
                        .header("Authorization", "Bearer " + tokenFor(ownerId))
                        .contentType("application/json")
                        .content(
                                """
                                {
                                  "query": "Spring CORS",
                                  "filters": {
                                    "technologies": ["spring security"],
                                    "errorPhrases": ["cors"]
                                  },
                                  "pageSize": 10
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.degradationMode").value("LEXICAL"))
                .andExpect(jsonPath("$.results[0].problemSessionId").value(corsSessionId.toString()))
                .andExpect(jsonPath("$.results[0].evidenceCodes[0]").value("LEXICAL_TEXT"))
                .andExpect(jsonPath("$.results[0].evidenceCodes[1]").value("ERROR_PHRASE"))
                .andExpect(jsonPath("$.results[0].evidenceCodes[2]").value("TECHNOLOGY"));
    }

    @Test
    void enforcesPageCapAndCursorFingerprint() throws Exception {
        MvcResult firstPage = mockMvc.perform(post("/api/v1/problem-session-searches")
                        .header("Authorization", "Bearer " + tokenFor(ownerId))
                        .contentType("application/json")
                        .content(
                                """
                                {
                                  "filters": {
                                    "technologies": ["spring security"]
                                  },
                                  "pageSize": 1
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results.length()").value(1))
                .andExpect(jsonPath("$.nextCursor").isNotEmpty())
                .andReturn();

        var nextCursor = JsonFieldExtractor.stringField(firstPage.getResponse().getContentAsString(), "nextCursor");

        mockMvc.perform(post("/api/v1/problem-session-searches")
                        .header("Authorization", "Bearer " + tokenFor(ownerId))
                        .contentType("application/json")
                        .content(
                                """
                                {
                                  "filters": {
                                    "technologies": ["spring security"]
                                  },
                                  "pageSize": 1,
                                  "cursor": "%s"
                                }
                                """
                                        .formatted(nextCursor)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results[0].problemSessionId").value(csrfSessionId.toString()));

        mockMvc.perform(post("/api/v1/problem-session-searches")
                        .header("Authorization", "Bearer " + tokenFor(ownerId))
                        .contentType("application/json")
                        .content(
                                """
                                {
                                  "filters": {
                                    "technologies": ["react"]
                                  },
                                  "pageSize": 1,
                                  "cursor": "%s"
                                }
                                """
                                        .formatted(nextCursor)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SEARCH_CURSOR_FILTER_MISMATCH"));
    }

    @Test
    void appliesProblemTypeFiltersAndNeverReturnsOtherUsersResults() throws Exception {
        mockMvc.perform(post("/api/v1/problem-session-searches")
                        .header("Authorization", "Bearer " + tokenFor(ownerId))
                        .contentType("application/json")
                        .content(
                                """
                                {
                                  "filters": {
                                    "technologies": ["react", "spring security"],
                                    "problemTypes": ["frontend"]
                                  },
                                  "pageSize": 10
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results.length()").value(1))
                .andExpect(jsonPath("$.results[0].problemSessionId").value(reactSessionId.toString()));

        mockMvc.perform(post("/api/v1/problem-session-searches")
                        .header("Authorization", "Bearer " + tokenFor(ownerId))
                        .contentType("application/json")
                        .content("{\"filters\":{\"problemTypes\":[\"configuration\"]}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results.length()").value(2))
                .andExpect(jsonPath("$.results[0].problemSessionId").value(corsSessionId.toString()))
                .andExpect(jsonPath("$.results[1].problemSessionId").value(csrfSessionId.toString()));

        assertThat(jdbcTemplate.queryForObject("select count(*) from problem_sessions", Integer.class))
                .isEqualTo(4);
    }

    @Test
    void emptySearchSignalsReturnRecentSolvedProblems() throws Exception {
        mockMvc.perform(post("/api/v1/problem-session-searches")
                        .header("Authorization", "Bearer " + tokenFor(ownerId))
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results.length()").value(3))
                .andExpect(jsonPath("$.results[0].problemSessionId").value(corsSessionId.toString()))
                .andExpect(jsonPath("$.results[0].evidenceCodes.length()").value(0))
                .andExpect(jsonPath("$.results[1].problemSessionId").value(csrfSessionId.toString()))
                .andExpect(jsonPath("$.results[2].problemSessionId").value(reactSessionId.toString()));
    }

    @Test
    void rejectsMissingAuthenticationOversizedPagesAndInvalidSearchSignals() throws Exception {
        mockMvc.perform(post("/api/v1/problem-session-searches")
                        .contentType("application/json")
                        .content("{\"query\":\"spring\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));

        mockMvc.perform(post("/api/v1/problem-session-searches")
                        .header("Authorization", "Bearer " + tokenFor(ownerId))
                        .contentType("application/json")
                        .content("{\"query\":\"spring\",\"pageSize\":51}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SEARCH_PAGE_SIZE_OUT_OF_RANGE"));

    }

    @Test
    void rejectsUnknownSearchFieldsAndNestedFilterMassAssignment() throws Exception {
        mockMvc.perform(post("/api/v1/problem-session-searches")
                        .header("Authorization", "Bearer " + tokenFor(ownerId))
                        .contentType("application/json")
                        .content(
                                """
                                {
                                  "query": "Spring CORS",
                                  "userId": "%s",
                                  "filters": {
                                    "technologies": ["spring security"]
                                  }
                                }
                                """.formatted(ownerId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SEARCH_INVALID_REQUEST"));

        mockMvc.perform(post("/api/v1/problem-session-searches")
                        .header("Authorization", "Bearer " + tokenFor(ownerId))
                        .contentType("application/json")
                        .content(
                                """
                                {
                                  "query": "Spring CORS",
                                  "filters": {
                                    "technologies": ["spring security"],
                                    "ownerId": "%s"
                                  }
                                }
                                """.formatted(ownerId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SEARCH_INVALID_REQUEST"));
    }

    private static UUID insertUser(Connection connection, String email) throws Exception {
        var userId = UUID.randomUUID();
        try (var statement =
                connection.prepareStatement(
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
                        """)) {
            statement.setObject(1, userId);
            statement.setString(2, email);
            statement.setString(3, email);
            statement.setString(4, "$argon2id$v=19$m=65536,t=3,p=2$synthetic$safehash");
            statement.setString(5, "m=65536,t=3,p=2");
            statement.executeUpdate();
        }
        return userId;
    }

    private static void insertSolvedSession(
            Connection connection,
            UUID userId,
            UUID problemSessionId,
            String title,
            String finalSolution,
            String[] technologies,
            String[] errorPhrases,
            String problemType,
            String searchDocument,
            String updatedAt)
            throws Exception {
        try (var statement = connection.prepareStatement(
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
                    technologies,
                    error_phrases,
                    problem_type,
                    search_document,
                    created_at,
                    updated_at
                ) values (
                    ?, ?, 'SOLVED', 'MANUAL', ?, ?, 'USER',
                    '2026-07-10T10:00:00Z'::timestamptz,
                    '2026-07-10T09:00:00Z'::timestamptz,
                    '2026-07-10T10:00:00Z'::timestamptz,
                    ?, ?, ?, ?, '2026-07-10T09:00:00Z'::timestamptz,
                    ?::timestamptz
                )
                """)) {
            statement.setObject(1, problemSessionId);
            statement.setObject(2, userId);
            statement.setString(3, title);
            statement.setString(4, finalSolution);
            statement.setArray(5, connection.createArrayOf("text", technologies));
            statement.setArray(6, connection.createArrayOf("text", errorPhrases));
            statement.setString(7, problemType);
            statement.setString(8, searchDocument);
            statement.setString(9, updatedAt);
            statement.executeUpdate();
        }
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

    private static final class JsonFieldExtractor {

        private JsonFieldExtractor() {}

        static String stringField(String json, String fieldName) {
            var field = "\"" + fieldName + "\":\"";
            var start = json.indexOf(field);
            if (start < 0) {
                throw new IllegalArgumentException("JSON field not found: " + fieldName);
            }
            var valueStart = start + field.length();
            var valueEnd = json.indexOf('"', valueStart);
            if (valueEnd < 0) {
                throw new IllegalArgumentException("JSON string value not closed: " + fieldName);
            }
            return json.substring(valueStart, valueEnd);
        }
    }
}
