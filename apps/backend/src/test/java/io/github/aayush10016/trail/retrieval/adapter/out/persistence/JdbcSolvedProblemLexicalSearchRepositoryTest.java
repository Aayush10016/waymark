package io.github.aayush10016.trail.retrieval.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.aayush10016.trail.retrieval.domain.model.SearchEvidenceCode;
import io.github.aayush10016.trail.retrieval.domain.model.SolvedProblemSearchQuery;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.List;
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
class JdbcSolvedProblemLexicalSearchRepositoryTest {

    private static final String POSTGRES_PGVECTOR_IMAGE =
            "pgvector/pgvector:0.8.2-pg17@sha256:feb68f4f15446397d8cac7f4fe48fe4586de83160d1fc48b46283312d1a33966";
    private static final UUID CORS_SESSION_ID = UUID.fromString("20000000-0000-4000-8000-000000000066");
    private static final UUID SECURITY_SESSION_ID = UUID.fromString("20000000-0000-4000-8000-000000000067");
    private static final UUID REACT_SESSION_ID = UUID.fromString("20000000-0000-4000-8000-000000000068");

    @Container
    static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>(
                            DockerImageName.parse(POSTGRES_PGVECTOR_IMAGE)
                                    .asCompatibleSubstituteFor("postgres"))
                    .withDatabaseName("trail_solved_search_repository_test")
                    .withUsername("trail")
                    .withPassword("trail_local_password");

    private JdbcSolvedProblemLexicalSearchRepository repository;
    private UUID userId;
    private UUID otherUserId;

    @BeforeEach
    void migrateAndSeed() throws SQLException {
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
        repository = new JdbcSolvedProblemLexicalSearchRepository(new JdbcTemplate(dataSource));

        try (var connection = connection()) {
            userId = insertUser(connection, "search-owner@example.com");
            otherUserId = insertUser(connection, "search-other@example.com");
            insertSolvedSession(
                    connection,
                    userId,
                    CORS_SESSION_ID,
                    "Spring Security CORS not working",
                    "Allowed localhost in CorsConfiguration and registered JWT filter before username password filter.",
                    new String[] {"spring security", "jwt"},
                    new String[] {"cors"},
                    "CONFIGURATION",
                    "Spring Security CORS not working Allowed localhost CorsConfiguration JWT filter",
                    "2026-07-13T09:00:00Z");
            insertSolvedSession(
                    connection,
                    userId,
                    SECURITY_SESSION_ID,
                    "Spring Security CSRF token rejected",
                    "Disabled CSRF for stateless API endpoints.",
                    new String[] {"spring security"},
                    new String[] {"403"},
                    "CONFIGURATION",
                    "Spring Security CSRF token rejected stateless API 403",
                    "2026-07-12T09:00:00Z");
            insertSolvedSession(
                    connection,
                    userId,
                    REACT_SESSION_ID,
                    "React hydration mismatch",
                    "Moved date formatting to client effect.",
                    new String[] {"react"},
                    new String[] {"hydration"},
                    "FRONTEND",
                    "React hydration mismatch client effect",
                    "2026-07-11T09:00:00Z");
            insertSolvedSession(
                    connection,
                    otherUserId,
                    UUID.randomUUID(),
                    "Spring Security CORS other user",
                    "Other user solution must never appear.",
                    new String[] {"spring security"},
                    new String[] {"cors"},
                    "CONFIGURATION",
                    "Spring Security CORS other user",
                    "2026-07-14T09:00:00Z");
        }
    }

    @Test
    void searchesOnlyUserOwnedSolvedSessionsWithDeterministicEvidenceAndWeights() {
        var results = repository.search(new SolvedProblemSearchQuery(
                userId,
                "spring cors",
                List.of("spring security"),
                List.of("cors"),
                10));

        assertThat(results).extracting(result -> result.problemSessionId()).contains(CORS_SESSION_ID);
        assertThat(results).extracting(result -> result.problemSessionId()).doesNotContain(REACT_SESSION_ID);
        assertThat(results.get(0).problemSessionId()).isEqualTo(CORS_SESSION_ID);
        assertThat(results.get(0).evidenceCodes())
                .containsExactly(
                        SearchEvidenceCode.LEXICAL_TEXT,
                        SearchEvidenceCode.ERROR_PHRASE,
                        SearchEvidenceCode.TECHNOLOGY);
    }

    @Test
    void ranksExactErrorAboveSameTechnologyHardNegative() {
        var results = repository.search(new SolvedProblemSearchQuery(
                userId,
                null,
                List.of("spring security"),
                List.of("cors"),
                10));

        assertThat(results.get(0).problemSessionId()).isEqualTo(CORS_SESSION_ID);
        assertThat(results.get(0).score()).isGreaterThan(results.get(1).score());
        assertThat(results.get(1).problemSessionId()).isEqualTo(SECURITY_SESSION_ID);
    }

    @Test
    void recentOnlySearchReturnsMostRecentlyUpdatedSolvedSessionsForCurrentUser() {
        var results = repository.search(new SolvedProblemSearchQuery(
                userId,
                null,
                List.of(),
                List.of(),
                10));

        assertThat(results)
                .extracting(result -> result.problemSessionId())
                .containsExactly(CORS_SESSION_ID, SECURITY_SESSION_ID, REACT_SESSION_ID);
        assertThat(results).allSatisfy(result -> {
            assertThat(result.score()).isZero();
            assertThat(result.evidenceCodes()).isEmpty();
        });
    }

    @Test
    void problemTypeOnlySearchReturnsMatchingSolvedSessionsForCurrentUser() {
        var results = repository.search(new SolvedProblemSearchQuery(
                userId,
                null,
                List.of(),
                List.of(),
                List.of("configuration"),
                10,
                0));

        assertThat(results)
                .extracting(result -> result.problemSessionId())
                .containsExactly(CORS_SESSION_ID, SECURITY_SESSION_ID);
        assertThat(results).allSatisfy(result -> {
            assertThat(result.problemType()).isEqualTo("CONFIGURATION");
            assertThat(result.evidenceCodes()).isEmpty();
        });
    }

    @Test
    void queryPlanUsesSolvedSessionSearchIndexes() throws SQLException {
        try (var connection = connection();
                var statement = connection.createStatement()) {
            statement.execute("set enable_seqscan = off");
            try (var resultSet = statement.executeQuery(
                    """
                    explain
                    select id
                    from problem_sessions
                    where user_id = '%s'::uuid
                        and status = 'SOLVED'
                        and deleted_at is null
                        and (
                            search_vector @@ plainto_tsquery('simple', 'spring cors')
                            or error_phrases && array['cors']::text[]
                            or technologies && array['spring security']::text[]
                        )
                    order by updated_at desc, id desc
                    limit 10
                    """.formatted(userId))) {
                var plan = new StringBuilder();
                while (resultSet.next()) {
                    plan.append(resultSet.getString(1)).append('\n');
                }
                assertThat(plan.toString())
                        .containsAnyOf(
                                "idx_problem_sessions_search_vector",
                                "idx_problem_sessions_error_phrases_gin",
                                "idx_problem_sessions_technologies_gin",
                                "idx_problem_sessions_solved_user_updated");
            }
        }
    }

    @Test
    void adversarialSearchSignalsAreParameterizedAndCannotChangeSqlSemantics() {
        var results = repository.search(new SolvedProblemSearchQuery(
                userId,
                "spring'); drop table users; --",
                List.of("spring security') or true --"),
                List.of("cors' or '1'='1"),
                List.of("configuration') or status = 'SOLVED"),
                10,
                0));

        assertThat(results).isEmpty();
        assertThat(tableExists("users")).isTrue();
        assertThat(tableExists("problem_sessions")).isTrue();
        assertThat(repository.search(new SolvedProblemSearchQuery(
                        userId,
                        "spring cors",
                        List.of("spring security"),
                        List.of("cors"),
                        10)))
                .extracting(result -> result.problemSessionId())
                .contains(CORS_SESSION_ID)
                .doesNotContain(REACT_SESSION_ID);
    }

    private static Connection connection() throws SQLException {
        return DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
    }

    private static boolean tableExists(String tableName) {
        return Boolean.TRUE.equals(new JdbcTemplate(dataSource()).queryForObject(
                "select to_regclass(?) is not null",
                Boolean.class,
                "public." + tableName));
    }

    private static PGSimpleDataSource dataSource() {
        var dataSource = new PGSimpleDataSource();
        dataSource.setUrl(postgres.getJdbcUrl());
        dataSource.setUser(postgres.getUsername());
        dataSource.setPassword(postgres.getPassword());
        return dataSource;
    }

    private static UUID insertUser(Connection connection, String email) throws SQLException {
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
            throws SQLException {
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
}
