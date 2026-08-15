package io.github.aayush10016.trail.retrieval.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.aayush10016.trail.retrieval.domain.model.ProblemVectorCandidateQuery;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
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
class JdbcProblemVectorCandidateRepositoryTest {

    private static final String POSTGRES_PGVECTOR_IMAGE =
            "pgvector/pgvector:0.8.2-pg17@sha256:feb68f4f15446397d8cac7f4fe48fe4586de83160d1fc48b46283312d1a33966";
    private static final UUID NEAREST_SESSION_ID = UUID.fromString("40000000-0000-4000-8000-000000000068");
    private static final UUID FARTHER_SESSION_ID = UUID.fromString("40000000-0000-4000-8000-000000000069");
    private static final UUID INACTIVE_GENERATION_SESSION_ID = UUID.fromString("40000000-0000-4000-8000-000000000070");
    private static final UUID OTHER_USER_SESSION_ID = UUID.fromString("40000000-0000-4000-8000-000000000071");

    @Container
    static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>(
                            DockerImageName.parse(POSTGRES_PGVECTOR_IMAGE)
                                    .asCompatibleSubstituteFor("postgres"))
                    .withDatabaseName("trail_vector_candidate_repository_test")
                    .withUsername("trail")
                    .withPassword("trail_local_password");

    private JdbcProblemVectorCandidateRepository repository;
    private UUID userId;

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
        repository = new JdbcProblemVectorCandidateRepository(new JdbcTemplate(dataSource), Duration.ofSeconds(2));

        try (var connection = connection()) {
            userId = insertUser(connection, "vector-owner@example.com");
            var otherUserId = insertUser(connection, "vector-other@example.com");
            insertSolvedSession(connection, userId, NEAREST_SESSION_ID, "Nearest", "2026-07-13T10:00:00Z");
            insertSolvedSession(connection, userId, FARTHER_SESSION_ID, "Farther", "2026-07-13T09:00:00Z");
            insertSolvedSession(connection, userId, INACTIVE_GENERATION_SESSION_ID, "Inactive", "2026-07-13T11:00:00Z");
            insertSolvedSession(connection, otherUserId, OTHER_USER_SESSION_ID, "Other", "2026-07-13T12:00:00Z");
            insertInactiveGeneration(connection);
            insertEmbedding(connection, userId, NEAREST_SESSION_ID, 1, true, "1".repeat(64), "[1,0,0]");
            insertEmbedding(connection, userId, FARTHER_SESSION_ID, 1, true, "2".repeat(64), "[0.8,0.2,0]");
            insertEmbedding(connection, userId, INACTIVE_GENERATION_SESSION_ID, 2, true, "3".repeat(64), "[1,0,0]");
            insertEmbedding(connection, otherUserId, OTHER_USER_SESSION_ID, 1, true, "4".repeat(64), "[1,0,0]");
        }
    }

    @Test
    void findsOnlyUserOwnedActiveGenerationCandidatesOrderedByExactDistance() {
        var candidates = repository.findCandidates(new ProblemVectorCandidateQuery(
                userId, "solved-session", List.of(1.0, 0.0, 0.0), 10, Duration.ofSeconds(2)));

        assertThat(candidates)
                .extracting(candidate -> candidate.problemSessionId())
                .containsExactly(NEAREST_SESSION_ID, FARTHER_SESSION_ID);
        assertThat(candidates.get(0).distance()).isZero();
        assertThat(candidates.get(1).distance()).isGreaterThan(candidates.get(0).distance());
        assertThat(candidates)
                .extracting(candidate -> candidate.problemSessionId())
                .doesNotContain(INACTIVE_GENERATION_SESSION_ID, OTHER_USER_SESSION_ID);
    }

    @Test
    void enforcesLimitAndConfiguredPositiveTimeout() {
        var limitedCandidates = repository.findCandidates(new ProblemVectorCandidateQuery(
                userId, "solved-session", List.of(1.0, 0.0, 0.0), 1, Duration.ofMillis(1)));

        assertThat(limitedCandidates).hasSize(1);
        assertThat(repository.defaultQueryTimeout()).isEqualTo(Duration.ofSeconds(2));
        assertThatThrownBy(() -> new JdbcProblemVectorCandidateRepository(
                        new JdbcTemplate(), Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void queryPlanUsesUserGenerationActiveIndexWithoutApproximateVectorIndex() throws SQLException {
        try (var connection = connection();
                var statement = connection.createStatement()) {
            statement.execute("set enable_seqscan = off");
            try (var resultSet = statement.executeQuery(
                    """
                    explain
                    select pe.problem_session_id
                    from problem_embeddings pe
                    join embedding_model_generations emg
                        on emg.generation_key = pe.generation_key
                        and emg.generation_version = pe.generation_version
                        and emg.active = true
                    join problem_sessions ps
                        on ps.user_id = pe.user_id
                        and ps.id = pe.problem_session_id
                    where pe.user_id = '%s'::uuid
                        and pe.generation_key = 'solved-session'
                        and pe.active = true
                        and ps.status = 'SOLVED'
                        and ps.deleted_at is null
                    order by pe.embedding <=> '[1,0,0]'::vector
                    limit 10
                    """.formatted(userId))) {
                var plan = new StringBuilder();
                while (resultSet.next()) {
                    plan.append(resultSet.getString(1)).append('\n');
                }
                assertThat(plan.toString())
                        .containsAnyOf(
                                "idx_problem_embeddings_user_generation_active",
                                "uq_problem_embeddings_active_generation");
                assertThat(plan.toString()).doesNotContainIgnoringCase("hnsw");
            }
        }
    }

    @Test
    void rejectsNonFiniteVectorValuesBeforeSqlConstruction() {
        assertThatThrownBy(() -> repository.findCandidates(new ProblemVectorCandidateQuery(
                        userId,
                        "solved-session",
                        List.of(1.0, Double.NaN, 0.0),
                        10,
                        Duration.ofSeconds(2))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("finite");
        assertThatThrownBy(() -> repository.findCandidates(new ProblemVectorCandidateQuery(
                        userId,
                        "solved-session",
                        List.of(1.0, Double.POSITIVE_INFINITY, 0.0),
                        10,
                        Duration.ofSeconds(2))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("finite");
    }

    private static Connection connection() throws SQLException {
        return DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
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
            Connection connection, UUID userId, UUID problemSessionId, String title, String updatedAt)
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
                    search_document,
                    created_at,
                    updated_at
                ) values (
                    ?, ?, 'SOLVED', 'MANUAL', ?, 'Solved with documented fix.', 'USER',
                    '2026-07-13T09:00:00Z'::timestamptz,
                    '2026-07-13T08:00:00Z'::timestamptz,
                    '2026-07-13T09:00:00Z'::timestamptz,
                    ?, '2026-07-13T08:00:00Z'::timestamptz,
                    ?::timestamptz
                )
                """)) {
            statement.setObject(1, problemSessionId);
            statement.setObject(2, userId);
            statement.setString(3, title);
            statement.setString(4, title);
            statement.setString(5, updatedAt);
            statement.executeUpdate();
        }
    }

    private static void insertInactiveGeneration(Connection connection) throws SQLException {
        try (var statement = connection.prepareStatement(
                """
                insert into embedding_model_generations (
                    id,
                    generation_key,
                    generation_version,
                    provider,
                    model,
                    dimensions,
                    active
                ) values (?, 'solved-session', 2, 'mock', 'trail-mock-embedding', 3, false)
                """)) {
            statement.setObject(1, UUID.fromString("40000000-0000-4000-8000-000000000001"));
            statement.executeUpdate();
        }
    }

    private static void insertEmbedding(
            Connection connection,
            UUID userId,
            UUID problemSessionId,
            int generationVersion,
            boolean active,
            String fingerprint,
            String vector)
            throws SQLException {
        try (var statement = connection.prepareStatement(
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
                    embedding,
                    active,
                    superseded_at,
                    created_at,
                    updated_at
                ) values (
                    ?, ?, ?, 'solved-session', ?, 'mock', 'trail-mock-embedding', 3,
                    ?, 'embedding-policy-v1', ?::vector, ?, null,
                    '2026-07-13T09:00:00Z'::timestamptz,
                    '2026-07-13T09:00:00Z'::timestamptz
                )
                """)) {
            statement.setObject(1, UUID.randomUUID());
            statement.setObject(2, userId);
            statement.setObject(3, problemSessionId);
            statement.setInt(4, generationVersion);
            statement.setString(5, fingerprint);
            statement.setString(6, vector);
            statement.setBoolean(7, active);
            statement.executeUpdate();
        }
    }
}
