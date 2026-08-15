package io.github.aayush10016.trail.retrieval.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
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
class JdbcRecommendationSuppressionRepositoryTest {

    private static final String POSTGRES_PGVECTOR_IMAGE =
            "pgvector/pgvector:0.8.2-pg17@sha256:feb68f4f15446397d8cac7f4fe48fe4586de83160d1fc48b46283312d1a33966";
    private static final UUID CURRENT_SESSION_ID = UUID.fromString("79000000-0000-4000-8000-000000000001");
    private static final UUID RECOMMENDED_SESSION_ID = UUID.fromString("79000000-0000-4000-8000-000000000002");
    private static final UUID OTHER_RECOMMENDED_SESSION_ID = UUID.fromString("79000000-0000-4000-8000-000000000003");
    private static final UUID OTHER_CURRENT_SESSION_ID = UUID.fromString("79000000-0000-4000-8000-000000000004");
    private static final UUID OTHER_USER_RECOMMENDED_SESSION_ID = UUID.fromString("79000000-0000-4000-8000-000000000005");

    @Container
    static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>(
                            DockerImageName.parse(POSTGRES_PGVECTOR_IMAGE)
                                    .asCompatibleSubstituteFor("postgres"))
                    .withDatabaseName("trail_recommendation_suppression_test")
                    .withUsername("trail")
                    .withPassword("trail_local_password");

    private JdbcRecommendationSuppressionRepository repository;
    private UUID userId;
    private UUID otherUserId;

    @BeforeEach
    void migrateAndSeed() throws SQLException {
        var flyway = Flyway.configure()
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
        repository = new JdbcRecommendationSuppressionRepository(new JdbcTemplate(dataSource));

        try (var connection = connection()) {
            userId = insertUser(connection, "suppression-owner@example.com");
            otherUserId = insertUser(connection, "suppression-other@example.com");
            insertCandidate(connection, userId, CURRENT_SESSION_ID);
            insertSolved(connection, userId, RECOMMENDED_SESSION_ID);
            insertSolved(connection, userId, OTHER_RECOMMENDED_SESSION_ID);
            insertCandidate(connection, otherUserId, OTHER_CURRENT_SESSION_ID);
            insertSolved(connection, otherUserId, OTHER_USER_RECOMMENDED_SESSION_ID);
            insertRecommendationEvent(connection, userId, CURRENT_SESSION_ID, RECOMMENDED_SESSION_ID, "OPENED");
            insertRecommendationEvent(connection, userId, CURRENT_SESSION_ID, RECOMMENDED_SESSION_ID, "HELPFUL");
            insertRecommendationEvent(connection, userId, CURRENT_SESSION_ID, OTHER_RECOMMENDED_SESSION_ID, "NOT_RELATED");
            insertRecommendationEvent(
                    connection, otherUserId, OTHER_CURRENT_SESSION_ID, OTHER_USER_RECOMMENDED_SESSION_ID, "DISMISSED");
        }
    }

    @Test
    void suppressesOnlyNotRelatedAndDismissedForTheSameUserOwnedPair() {
        assertThat(repository.isSuppressed(userId, CURRENT_SESSION_ID, RECOMMENDED_SESSION_ID)).isFalse();
        assertThat(repository.isSuppressed(userId, CURRENT_SESSION_ID, OTHER_RECOMMENDED_SESSION_ID)).isTrue();
        assertThat(repository.isSuppressed(otherUserId, OTHER_CURRENT_SESSION_ID, OTHER_USER_RECOMMENDED_SESSION_ID)).isTrue();
        assertThat(repository.isSuppressed(userId, CURRENT_SESSION_ID, UUID.fromString("79000000-0000-4000-8000-000000000004")))
                .isFalse();
    }

    private static Connection connection() throws SQLException {
        return DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
    }

    private static UUID insertUser(Connection connection, String email) throws SQLException {
        var userId = UUID.randomUUID();
        try (var statement = connection.prepareStatement(
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

    private static void insertCandidate(Connection connection, UUID userId, UUID problemSessionId) throws SQLException {
        try (var statement = connection.prepareStatement(
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
                ) values (?, ?, 'CANDIDATE', 'MANUAL', 'Current', 'current', now(), now(), now())
                """)) {
            statement.setObject(1, problemSessionId);
            statement.setObject(2, userId);
            statement.executeUpdate();
        }
    }

    private static void insertSolved(Connection connection, UUID userId, UUID problemSessionId) throws SQLException {
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
                    search_document,
                    started_at,
                    ended_at,
                    created_at,
                    updated_at
                ) values (
                    ?, ?, 'SOLVED', 'MANUAL', 'Solved', 'Fixed it.', 'USER',
                    now(), 'solved', now(), now(), now(), now()
                )
                """)) {
            statement.setObject(1, problemSessionId);
            statement.setObject(2, userId);
            statement.executeUpdate();
        }
    }

    private static void insertRecommendationEvent(
            Connection connection, UUID userId, UUID currentSessionId, UUID recommendedSessionId, String outcome)
            throws SQLException {
        try (var statement = connection.prepareStatement(
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
                ) values (
                    ?, ?, ?, ?, ?, ?,
                    jsonb_build_array(jsonb_build_object('problemSessionId', ?::text)),
                    jsonb_build_array(jsonb_build_object('problemSessionId', ?::text, 'evidenceCode', 'ERROR_PHRASE')),
                    'solved-session',
                    1,
                    'mock',
                    'trail-mock-embedding',
                    ?,
                    'hybrid-similarity-v1'
                )
                """)) {
            statement.setObject(1, UUID.randomUUID());
            statement.setObject(2, userId);
            statement.setObject(3, UUID.nameUUIDFromBytes((currentSessionId + ":" + recommendedSessionId).getBytes()));
            statement.setObject(4, currentSessionId);
            statement.setObject(5, recommendedSessionId);
            statement.setString(6, outcome);
            statement.setString(7, recommendedSessionId.toString());
            statement.setString(8, recommendedSessionId.toString());
            statement.setString(9, "7".repeat(64));
            statement.executeUpdate();
        }
    }
}
