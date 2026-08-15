package io.github.aayush10016.trail.database;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.OffsetDateTime;
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
class ProblemSessionSchemaMigrationTest {

    private static final String POSTGRES_PGVECTOR_IMAGE =
            "pgvector/pgvector:0.8.2-pg17@sha256:feb68f4f15446397d8cac7f4fe48fe4586de83160d1fc48b46283312d1a33966";

    @Container
    static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>(
                            DockerImageName.parse(POSTGRES_PGVECTOR_IMAGE)
                                    .asCompatibleSubstituteFor("postgres"))
                    .withDatabaseName("trail_problem_session_schema_test")
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
    void createsProblemSessionAndArtifactTablesWithCriticalIndexes() throws SQLException {
        try (var connection = connection()) {
            assertThat(tableNames(connection)).contains("problem_sessions", "session_artifacts");
            assertThat(indexNames(connection))
                    .contains(
                            "idx_problem_sessions_user_status_updated",
                            "idx_problem_sessions_user_active",
                            "idx_problem_sessions_search_vector",
                            "uq_session_artifacts_active_position",
                            "idx_session_artifacts_problem_position",
                            "idx_session_artifacts_user_problem_position");
        }
    }

    @Test
    void enforcesProblemSessionLifecycleAndSolutionInvariants() throws SQLException {
        try (var connection = connection()) {
            var userId = insertUser(connection, "problem-invariants@example.com");

            insertProblemSession(connection, userId, UUID.randomUUID(), "CANDIDATE", null, null);
            insertProblemSession(connection, userId, UUID.randomUUID(), "SOLVED", "Allowed localhost:3000.", "USER");

            assertThatThrownBy(() -> insertProblemSession(connection, userId, UUID.randomUUID(), "SOLVED", null, null))
                    .isInstanceOf(PSQLException.class);
            assertThatThrownBy(() -> insertProblemSession(
                            connection, userId, UUID.randomUUID(), "REVIEWABLE", "Not final yet.", "USER"))
                    .isInstanceOf(PSQLException.class);
            assertThatThrownBy(() -> insertProblemSession(
                            connection, userId, UUID.randomUUID(), "SOLVED", "Generated answer.", "AI"))
                    .isInstanceOf(PSQLException.class);
            assertThatThrownBy(
                            () -> insertProblemSession(connection, userId, UUID.randomUUID(), "UNKNOWN", null, null))
                    .isInstanceOf(PSQLException.class);
        }
    }

    @Test
    void enforcesCompositeOwnershipAndActiveArtifactPositions() throws SQLException {
        try (var connection = connection()) {
            var firstUserId = insertUser(connection, "first-artifact-owner@example.com");
            var secondUserId = insertUser(connection, "second-artifact-owner@example.com");
            var problemSessionId = UUID.randomUUID();

            insertProblemSession(connection, firstUserId, problemSessionId, "CANDIDATE", null, null);
            insertArtifact(connection, firstUserId, problemSessionId, 1, null);

            assertThatThrownBy(() -> insertArtifact(connection, secondUserId, problemSessionId, 2, null))
                    .isInstanceOf(PSQLException.class);
            assertThatThrownBy(() -> insertArtifact(connection, firstUserId, problemSessionId, 1, null))
                    .isInstanceOf(PSQLException.class);

            insertArtifact(connection, firstUserId, problemSessionId, 1, OffsetDateTime.now().plusSeconds(1));
        }
    }

    @Test
    void linksCaptureCandidateProblemSessionWithCompositeOwnership() throws SQLException {
        try (var connection = connection()) {
            var firstUserId = insertUser(connection, "candidate-owner@example.com");
            var secondUserId = insertUser(connection, "candidate-other@example.com");
            var problemSessionId = UUID.randomUUID();

            insertProblemSession(connection, firstUserId, problemSessionId, "CANDIDATE", null, null);
            insertCaptureEvent(connection, firstUserId, "candidate-valid", problemSessionId);

            assertThatThrownBy(
                            () -> insertCaptureEvent(connection, secondUserId, "candidate-cross-user", problemSessionId))
                    .isInstanceOf(PSQLException.class);
            assertThatThrownBy(() -> insertCaptureEvent(
                            connection, firstUserId, "candidate-missing-session", UUID.randomUUID()))
                    .isInstanceOf(PSQLException.class);
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

    private static void insertProblemSession(
            Connection connection,
            UUID userId,
            UUID problemSessionId,
            String status,
            String finalSolution,
            String finalSolutionAuthor)
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
                        ) values (?, ?, ?, 'MANUAL', 'Spring Security CORS not working', ?, ?, ?::timestamptz, ?::timestamptz, ?)
                        """)) {
            statement.setObject(1, problemSessionId);
            statement.setObject(2, userId);
            statement.setString(3, status);
            statement.setString(4, finalSolution);
            statement.setString(5, finalSolutionAuthor);
            statement.setString(6, finalSolution == null ? null : "2026-07-13T09:00:00Z");
            statement.setString(7, "2026-07-13T08:00:00Z");
            statement.setString(8, "Spring Security CORS not working");
            statement.executeUpdate();
        }
    }

    private static void insertArtifact(
            Connection connection, UUID userId, UUID problemSessionId, int position, OffsetDateTime removedAt)
            throws SQLException {
        try (var statement =
                connection.prepareStatement(
                        """
                        insert into session_artifacts (
                            id,
                            user_id,
                            problem_session_id,
                            position,
                            artifact_type,
                            sanitized_url,
                            canonical_url_hash,
                            hostname,
                            title,
                            occurred_at,
                            removed_at
                        ) values (?, ?, ?, ?, 'DOCUMENTATION', ?, ?, 'docs.spring.io', 'Spring Security CORS', ?::timestamptz, ?)
                        """)) {
            statement.setObject(1, UUID.randomUUID());
            statement.setObject(2, userId);
            statement.setObject(3, problemSessionId);
            statement.setInt(4, position);
            statement.setString(5, "https://docs.spring.io/spring-security/reference/servlet/integrations/cors.html");
            statement.setBytes(6, hashBytes(1));
            statement.setString(7, "2026-07-13T08:10:00Z");
            statement.setObject(8, removedAt);
            statement.executeUpdate();
        }
    }

    private static void insertCaptureEvent(Connection connection, UUID userId, String clientEventId, UUID candidateId)
            throws SQLException {
        try (var statement =
                connection.prepareStatement(
                        """
                        insert into capture_events (
                            id,
                            user_id,
                            client_event_id,
                            client_sequence,
                            client_instance_id,
                            tab_session_id,
                            source,
                            resource_type,
                            navigation_type,
                            sanitized_url,
                            canonical_url_hash,
                            hostname,
                            title,
                            removed_query_parameters,
                            transition_qualifiers,
                            policy_version,
                            browser_event_time_ms,
                            occurred_at,
                            candidate_problem_session_id,
                            retention_expires_at
                        ) values (
                            ?, ?, ?, 1, 'extension-instance-1', 'tab-session-1',
                            'WEB_NAVIGATION_COMMITTED', 'DOCUMENTATION', 'link',
                            ?, ?, 'docs.spring.io', 'Spring Security CORS',
                            array[]::text[], array[]::text[],
                            'url-query-sanitation-v1', 100, ?::timestamptz, ?, ?::timestamptz
                        )
                        """)) {
            statement.setObject(1, UUID.randomUUID());
            statement.setObject(2, userId);
            statement.setString(3, clientEventId);
            statement.setString(4, "https://docs.spring.io/spring-security/reference/servlet/integrations/cors.html");
            statement.setBytes(5, hashBytes(2));
            statement.setString(6, "2026-07-13T08:00:00Z");
            statement.setObject(7, candidateId);
            statement.setString(8, "2026-07-20T08:00:00Z");
            statement.executeUpdate();
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

    private static byte[] hashBytes(int seed) {
        var bytes = new byte[32];
        for (int index = 0; index < bytes.length; index += 1) {
            bytes[index] = (byte) (seed + index);
        }
        return bytes;
    }
}
