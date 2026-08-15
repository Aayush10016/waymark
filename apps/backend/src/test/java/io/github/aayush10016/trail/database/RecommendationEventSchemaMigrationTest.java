package io.github.aayush10016.trail.database;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.postgresql.util.PSQLException;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
class RecommendationEventSchemaMigrationTest {

    private static final String POSTGRES_PGVECTOR_IMAGE =
            "pgvector/pgvector:0.8.2-pg17@sha256:feb68f4f15446397d8cac7f4fe48fe4586de83160d1fc48b46283312d1a33966";
    private static final String SHA_256 = "0".repeat(64);

    @Container
    static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>(
                            DockerImageName.parse(POSTGRES_PGVECTOR_IMAGE)
                                    .asCompatibleSubstituteFor("postgres"))
                    .withDatabaseName("trail_recommendation_event_schema_test")
                    .withUsername("trail")
                    .withPassword("trail_local_password");

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
    void createsRecommendationEventTableWithCriticalIndexes() throws SQLException {
        try (var connection = connection()) {
            assertThat(tableNames(connection)).contains("recommendation_events");
            assertThat(indexNames(connection))
                    .contains(
                            "uq_recommendation_events_outcome_once",
                            "idx_recommendation_events_user_current_created",
                            "idx_recommendation_events_user_pair_outcome",
                            "idx_recommendation_events_user_recommendation_created");
        }
    }

    @Test
    void enforcesCompositeOwnershipForCurrentAndRecommendedSessions() throws SQLException {
        try (var connection = connection()) {
            var firstUserId = insertUser(connection, "recommendation-owner@example.com");
            var secondUserId = insertUser(connection, "recommendation-other@example.com");
            var currentSessionId = UUID.randomUUID();
            var recommendedSessionId = UUID.randomUUID();
            var otherRecommendedSessionId = UUID.randomUUID();
            insertCandidateProblemSession(connection, firstUserId, currentSessionId);
            insertSolvedProblemSession(connection, firstUserId, recommendedSessionId);
            insertSolvedProblemSession(connection, secondUserId, otherRecommendedSessionId);

            insertRecommendationEvent(connection, firstUserId, currentSessionId, recommendedSessionId, "SHOWN");

            assertThatThrownBy(() -> insertRecommendationEvent(
                            connection, firstUserId, currentSessionId, otherRecommendedSessionId, "OPENED"))
                    .isInstanceOf(PSQLException.class);
        }
    }

    @Test
    void enforcesRetrievedEvidenceProviderEmbeddingAlgorithmAndOutcomeConstraints() throws SQLException {
        try (var connection = connection()) {
            var userId = insertUser(connection, "recommendation-constraints@example.com");
            var currentSessionId = UUID.randomUUID();
            var recommendedSessionId = UUID.randomUUID();
            insertCandidateProblemSession(connection, userId, currentSessionId);
            insertSolvedProblemSession(connection, userId, recommendedSessionId);

            insertRecommendationEvent(connection, userId, currentSessionId, recommendedSessionId, "SHOWN");

            assertThatThrownBy(() -> insertRecommendationEventWithOverrides(
                            connection,
                            userId,
                            currentSessionId,
                            recommendedSessionId,
                            "BAD_OUTCOME",
                            retrievedJson(recommendedSessionId),
                            evidenceJson(recommendedSessionId),
                            "solved-session",
                            1,
                            "mock",
                            "trail-mock-embedding",
                            SHA_256,
                            "hybrid-similarity-v1"))
                    .isInstanceOf(PSQLException.class);
            assertThatThrownBy(() -> insertRecommendationEventWithOverrides(
                            connection,
                            userId,
                            currentSessionId,
                            recommendedSessionId,
                            "OPENED",
                            "{}",
                            evidenceJson(recommendedSessionId),
                            "solved-session",
                            1,
                            "mock",
                            "trail-mock-embedding",
                            SHA_256,
                            "hybrid-similarity-v1"))
                    .isInstanceOf(PSQLException.class);
            assertThatThrownBy(() -> insertRecommendationEventWithOverrides(
                            connection,
                            userId,
                            currentSessionId,
                            recommendedSessionId,
                            "HELPFUL",
                            retrievedJson(recommendedSessionId),
                            "[]",
                            "solved-session",
                            1,
                            "mock",
                            "trail-mock-embedding",
                            SHA_256,
                            "hybrid-similarity-v1"))
                    .isInstanceOf(PSQLException.class);
            assertThatThrownBy(() -> insertRecommendationEventWithOverrides(
                            connection,
                            userId,
                            currentSessionId,
                            recommendedSessionId,
                            "NOT_RELATED",
                            retrievedJson(recommendedSessionId),
                            evidenceJson(recommendedSessionId),
                            "solved-session",
                            99,
                            "mock",
                            "trail-mock-embedding",
                            SHA_256,
                            "hybrid-similarity-v1"))
                    .isInstanceOf(PSQLException.class);
            assertThatThrownBy(() -> insertRecommendationEventWithOverrides(
                            connection,
                            userId,
                            currentSessionId,
                            recommendedSessionId,
                            "DISMISSED",
                            retrievedJson(recommendedSessionId),
                            evidenceJson(recommendedSessionId),
                            "solved-session",
                            1,
                            "",
                            "trail-mock-embedding",
                            SHA_256,
                            "hybrid-similarity-v1"))
                    .isInstanceOf(PSQLException.class);
            assertThatThrownBy(() -> insertRecommendationEventWithOverrides(
                            connection,
                            userId,
                            currentSessionId,
                            recommendedSessionId,
                            "OPENED",
                            retrievedJson(recommendedSessionId),
                            evidenceJson(recommendedSessionId),
                            "solved-session",
                            1,
                            "mock",
                            "trail-mock-embedding",
                            "not-a-sha",
                            "hybrid-similarity-v1"))
                    .isInstanceOf(PSQLException.class);
            assertThatThrownBy(() -> insertRecommendationEventWithOverrides(
                            connection,
                            userId,
                            currentSessionId,
                            recommendedSessionId,
                            "HELPFUL",
                            retrievedJson(recommendedSessionId),
                            evidenceJson(recommendedSessionId),
                            "solved-session",
                            1,
                            "mock",
                            "trail-mock-embedding",
                            SHA_256,
                            ""))
                    .isInstanceOf(PSQLException.class);
        }
    }

    @Test
    void enforcesOneOutcomeEventPerRecommendationAndDeletesWithProblemSession() throws SQLException {
        try (var connection = connection()) {
            var userId = insertUser(connection, "recommendation-lifecycle@example.com");
            var currentSessionId = UUID.randomUUID();
            var recommendedSessionId = UUID.randomUUID();
            insertCandidateProblemSession(connection, userId, currentSessionId);
            insertSolvedProblemSession(connection, userId, recommendedSessionId);

            var recommendationId =
                    insertRecommendationEvent(connection, userId, currentSessionId, recommendedSessionId, "SHOWN");

            assertThatThrownBy(() -> insertRecommendationEvent(
                            connection, userId, currentSessionId, recommendedSessionId, "SHOWN", recommendationId))
                    .isInstanceOf(PSQLException.class);

            deleteProblemSession(connection, userId, currentSessionId);

            assertThat(recommendationEventCount(connection, recommendationId)).isZero();
        }
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

    private static void insertCandidateProblemSession(Connection connection, UUID userId, UUID problemSessionId)
            throws SQLException {
        insertProblemSession(connection, userId, problemSessionId, "CANDIDATE", null);
    }

    private static void insertSolvedProblemSession(Connection connection, UUID userId, UUID problemSessionId)
            throws SQLException {
        insertProblemSession(connection, userId, problemSessionId, "SOLVED", "Allowed localhost in CorsConfiguration.");
    }

    private static void insertProblemSession(
            Connection connection, UUID userId, UUID problemSessionId, String status, String finalSolution)
            throws SQLException {
        try (var statement =
                connection.prepareStatement(
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
                            search_document
                        ) values (
                            ?, ?, ?, 'MANUAL', 'Spring Security CORS not working',
                            ?, case when ? is null then null else 'USER' end,
                            case when ? is null then null else '2026-07-13T09:00:00Z'::timestamptz end,
                            '2026-07-13T08:00:00Z'::timestamptz,
                            'Spring Security CORS not working Allowed localhost in CorsConfiguration.'
                        )
                        """)) {
            statement.setObject(1, problemSessionId);
            statement.setObject(2, userId);
            statement.setString(3, status);
            statement.setString(4, finalSolution);
            statement.setString(5, finalSolution);
            statement.setString(6, finalSolution);
            statement.executeUpdate();
        }
    }

    private static UUID insertRecommendationEvent(
            Connection connection, UUID userId, UUID currentSessionId, UUID recommendedSessionId, String outcome)
            throws SQLException {
        return insertRecommendationEvent(connection, userId, currentSessionId, recommendedSessionId, outcome, UUID.randomUUID());
    }

    private static UUID insertRecommendationEvent(
            Connection connection,
            UUID userId,
            UUID currentSessionId,
            UUID recommendedSessionId,
            String outcome,
            UUID recommendationId)
            throws SQLException {
        insertRecommendationEventWithOverrides(
                connection,
                userId,
                currentSessionId,
                recommendedSessionId,
                outcome,
                retrievedJson(recommendedSessionId),
                evidenceJson(recommendedSessionId),
                "solved-session",
                1,
                "mock",
                "trail-mock-embedding",
                SHA_256,
                "hybrid-similarity-v1",
                recommendationId);
        return recommendationId;
    }

    private static void insertRecommendationEventWithOverrides(
            Connection connection,
            UUID userId,
            UUID currentSessionId,
            UUID recommendedSessionId,
            String outcome,
            String retrievedProblemSessions,
            String evidence,
            String generationKey,
            int generationVersion,
            String provider,
            String model,
            String embeddingFingerprint,
            String algorithmVersion)
            throws SQLException {
        insertRecommendationEventWithOverrides(
                connection,
                userId,
                currentSessionId,
                recommendedSessionId,
                outcome,
                retrievedProblemSessions,
                evidence,
                generationKey,
                generationVersion,
                provider,
                model,
                embeddingFingerprint,
                algorithmVersion,
                UUID.randomUUID());
    }

    private static void insertRecommendationEventWithOverrides(
            Connection connection,
            UUID userId,
            UUID currentSessionId,
            UUID recommendedSessionId,
            String outcome,
            String retrievedProblemSessions,
            String evidence,
            String generationKey,
            int generationVersion,
            String provider,
            String model,
            String embeddingFingerprint,
            String algorithmVersion,
            UUID recommendationId)
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
                ) values (?, ?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?, ?, ?, ?, ?, ?)
                """)) {
            statement.setObject(1, UUID.randomUUID());
            statement.setObject(2, userId);
            statement.setObject(3, recommendationId);
            statement.setObject(4, currentSessionId);
            statement.setObject(5, recommendedSessionId);
            statement.setString(6, outcome);
            statement.setString(7, retrievedProblemSessions);
            statement.setString(8, evidence);
            statement.setString(9, generationKey);
            statement.setInt(10, generationVersion);
            statement.setString(11, provider);
            statement.setString(12, model);
            statement.setString(13, embeddingFingerprint);
            statement.setString(14, algorithmVersion);
            statement.executeUpdate();
        }
    }

    private static String retrievedJson(UUID recommendedSessionId) {
        return """
                [{"problemSessionId":"%s","rank":1}]
                """
                .formatted(recommendedSessionId);
    }

    private static String evidenceJson(UUID recommendedSessionId) {
        return """
                [{"problemSessionId":"%s","code":"ERROR_PHRASE"}]
                """
                .formatted(recommendedSessionId);
    }

    private static void deleteProblemSession(Connection connection, UUID userId, UUID problemSessionId)
            throws SQLException {
        try (var statement = connection.prepareStatement("delete from problem_sessions where user_id = ? and id = ?")) {
            statement.setObject(1, userId);
            statement.setObject(2, problemSessionId);
            statement.executeUpdate();
        }
    }

    private static int recommendationEventCount(Connection connection, UUID recommendationId) throws SQLException {
        try (var statement = connection.prepareStatement(
                "select count(*) from recommendation_events where recommendation_id = ?")) {
            statement.setObject(1, recommendationId);
            try (var resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getInt(1);
            }
        }
    }

    private static List<String> tableNames(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
                var resultSet =
                        statement.executeQuery(
                                """
                                select table_name
                                from information_schema.tables
                                where table_schema = 'public'
                                order by table_name
                                """)) {
            var names = new java.util.ArrayList<String>();
            while (resultSet.next()) {
                names.add(resultSet.getString("table_name"));
            }
            return names;
        }
    }

    private static List<String> indexNames(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
                var resultSet =
                        statement.executeQuery(
                                """
                                select indexname
                                from pg_indexes
                                where schemaname = 'public'
                                order by indexname
                                """)) {
            var names = new java.util.ArrayList<String>();
            while (resultSet.next()) {
                names.add(resultSet.getString("indexname"));
            }
            return names;
        }
    }
}
