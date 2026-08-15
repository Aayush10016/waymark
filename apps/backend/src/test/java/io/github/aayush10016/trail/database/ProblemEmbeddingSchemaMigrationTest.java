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
class ProblemEmbeddingSchemaMigrationTest {

    private static final String POSTGRES_PGVECTOR_IMAGE =
            "pgvector/pgvector:0.8.2-pg17@sha256:feb68f4f15446397d8cac7f4fe48fe4586de83160d1fc48b46283312d1a33966";

    @Container
    static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>(
                            DockerImageName.parse(POSTGRES_PGVECTOR_IMAGE)
                                    .asCompatibleSubstituteFor("postgres"))
                    .withDatabaseName("trail_problem_embedding_schema_test")
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
    void createsProblemEmbeddingTablesWithCriticalIndexes() throws SQLException {
        try (var connection = connection()) {
            assertThat(tableNames(connection)).contains("embedding_model_generations", "problem_embeddings");
            assertThat(indexNames(connection))
                    .contains(
                            "uq_embedding_model_generations_key_version",
                            "uq_embedding_model_generations_one_active",
                            "uq_problem_embeddings_active_generation",
                            "uq_problem_embeddings_fingerprint",
                            "idx_problem_embeddings_user_generation_active");
        }
    }

    @Test
    void enforcesOneActiveGenerationPerKey() throws SQLException {
        try (var connection = connection()) {
            assertThatThrownBy(() -> insertGeneration(connection, "solved-session", 2, true, 3))
                    .isInstanceOf(PSQLException.class);

            insertGeneration(connection, "solved-session", 2, false, 3);
        }
    }

    @Test
    void enforcesDimensionCompositeOwnershipAndActiveUniqueness() throws SQLException {
        try (var connection = connection()) {
            var firstUserId = insertUser(connection, "embedding-owner@example.com");
            var secondUserId = insertUser(connection, "embedding-other@example.com");
            var problemSessionId = UUID.randomUUID();
            insertSolvedProblemSession(connection, firstUserId, problemSessionId);

            insertEmbedding(connection, firstUserId, problemSessionId, "[0.1,0.2,0.3]", 3, true, "1".repeat(64));

            assertThatThrownBy(() -> insertEmbedding(
                            connection, secondUserId, problemSessionId, "[0.1,0.2,0.3]", 3, true, "2".repeat(64)))
                    .isInstanceOf(PSQLException.class);
            assertThatThrownBy(() -> insertEmbedding(
                            connection, firstUserId, problemSessionId, "[0.1,0.2]", 3, true, "3".repeat(64)))
                    .isInstanceOf(PSQLException.class);
            assertThatThrownBy(() -> insertEmbedding(
                            connection, firstUserId, problemSessionId, "[0.4,0.5,0.6]", 3, true, "4".repeat(64)))
                    .isInstanceOf(PSQLException.class);

            insertEmbedding(connection, firstUserId, problemSessionId, "[0.4,0.5,0.6]", 3, false, "5".repeat(64));
        }
    }

    @Test
    void deletesProblemEmbeddingsWithOwningProblemSession() throws SQLException {
        try (var connection = connection()) {
            var userId = insertUser(connection, "embedding-delete@example.com");
            var problemSessionId = UUID.randomUUID();
            insertSolvedProblemSession(connection, userId, problemSessionId);
            insertEmbedding(connection, userId, problemSessionId, "[0.1,0.2,0.3]", 3, true, "6".repeat(64));

            deleteProblemSession(connection, userId, problemSessionId);

            assertThat(problemEmbeddingCount(connection, problemSessionId)).isZero();
        }
    }

    private static Connection connection() throws SQLException {
        return DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
    }

    private static void insertGeneration(Connection connection, String key, int version, boolean active, int dimensions)
            throws SQLException {
        try (var statement =
                connection.prepareStatement(
                        """
                        insert into embedding_model_generations (
                            id,
                            generation_key,
                            generation_version,
                            provider,
                            model,
                            dimensions,
                            active
                        ) values (?, ?, ?, 'mock', 'trail-mock-embedding', ?, ?)
                        """)) {
            statement.setObject(1, UUID.randomUUID());
            statement.setString(2, key);
            statement.setInt(3, version);
            statement.setInt(4, dimensions);
            statement.setBoolean(5, active);
            statement.executeUpdate();
        }
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

    private static void insertSolvedProblemSession(Connection connection, UUID userId, UUID problemSessionId)
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
                            ?, ?, 'SOLVED', 'MANUAL', 'Spring Security CORS not working',
                            'Allowed localhost in CorsConfiguration.', 'USER',
                            '2026-07-13T09:00:00Z'::timestamptz,
                            '2026-07-13T08:00:00Z'::timestamptz,
                            'Spring Security CORS not working'
                        )
                        """)) {
            statement.setObject(1, problemSessionId);
            statement.setObject(2, userId);
            statement.executeUpdate();
        }
    }

    private static void insertEmbedding(
            Connection connection,
            UUID userId,
            UUID problemSessionId,
            String vector,
            int dimensions,
            boolean active,
            String inputFingerprint)
            throws SQLException {
        try (var statement =
                connection.prepareStatement(
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
                            ?, ?, ?, 'solved-session', 1, 'mock', 'trail-mock-embedding',
                            ?, ?, 'embedding-policy-v1', ?::vector, ?, ?::timestamptz,
                            '2026-07-18T09:00:00Z'::timestamptz,
                            '2026-07-18T09:30:00Z'::timestamptz
                        )
                        """)) {
            statement.setObject(1, UUID.randomUUID());
            statement.setObject(2, userId);
            statement.setObject(3, problemSessionId);
            statement.setInt(4, dimensions);
            statement.setString(5, inputFingerprint);
            statement.setString(6, vector);
            statement.setBoolean(7, active);
            statement.setString(8, active ? null : "2026-07-18T09:30:00Z");
            statement.executeUpdate();
        }
    }

    private static void deleteProblemSession(Connection connection, UUID userId, UUID problemSessionId)
            throws SQLException {
        try (var statement = connection.prepareStatement("delete from problem_sessions where user_id = ? and id = ?")) {
            statement.setObject(1, userId);
            statement.setObject(2, problemSessionId);
            statement.executeUpdate();
        }
    }

    private static int problemEmbeddingCount(Connection connection, UUID problemSessionId) throws SQLException {
        try (var statement = connection.prepareStatement(
                "select count(*) from problem_embeddings where problem_session_id = ?")) {
            statement.setObject(1, problemSessionId);
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
