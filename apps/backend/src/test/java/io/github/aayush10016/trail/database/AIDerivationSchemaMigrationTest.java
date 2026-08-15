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
class AIDerivationSchemaMigrationTest {

    private static final String POSTGRES_PGVECTOR_IMAGE =
            "pgvector/pgvector:0.8.2-pg17@sha256:feb68f4f15446397d8cac7f4fe48fe4586de83160d1fc48b46283312d1a33966";
    private static final String SHA_256 = "0".repeat(64);

    @Container
    static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>(
                            DockerImageName.parse(POSTGRES_PGVECTOR_IMAGE)
                                    .asCompatibleSubstituteFor("postgres"))
                    .withDatabaseName("trail_ai_derivation_schema_test")
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
    void createsDerivationTableWithCriticalIndexes() throws SQLException {
        try (var connection = connection()) {
            assertThat(tableNames(connection)).contains("ai_derivations");
            assertThat(indexNames(connection))
                    .contains(
                            "uq_ai_derivations_success_fingerprint",
                            "idx_ai_derivations_problem_type_created",
                            "idx_ai_derivations_status_created");
        }
    }

    @Test
    void enforcesCompositeProblemSessionOwnership() throws SQLException {
        try (var connection = connection()) {
            var firstUserId = insertUser(connection, "ai-derivation-owner@example.com");
            var secondUserId = insertUser(connection, "ai-derivation-other@example.com");
            var problemSessionId = UUID.randomUUID();
            insertSolvedProblemSession(connection, firstUserId, problemSessionId);

            insertSuccessfulDerivation(connection, firstUserId, problemSessionId, "1".repeat(64));

            assertThatThrownBy(() -> insertSuccessfulDerivation(connection, secondUserId, problemSessionId, "2".repeat(64)))
                    .isInstanceOf(PSQLException.class);
        }
    }

    @Test
    void enforcesProviderPromptSchemaPolicyFingerprintStatusAndCostConstraints() throws SQLException {
        try (var connection = connection()) {
            var userId = insertUser(connection, "ai-derivation-constraints@example.com");
            var problemSessionId = UUID.randomUUID();
            insertSolvedProblemSession(connection, userId, problemSessionId);

            insertSuccessfulDerivation(connection, userId, problemSessionId, "3".repeat(64));

            assertThatThrownBy(() -> insertDerivation(connection, userId, problemSessionId, "BAD_TYPE", "SUCCEEDED", null,
                            "4".repeat(64), 0, 0, 0, 0))
                    .isInstanceOf(PSQLException.class);
            assertThatThrownBy(() -> insertDerivation(connection, userId, problemSessionId, "PROBLEM_SESSION_ENRICHMENT",
                            "SUCCEEDED", null, "not-a-hash", 0, 0, 0, 0))
                    .isInstanceOf(PSQLException.class);
            assertThatThrownBy(() -> insertDerivation(connection, userId, problemSessionId, "PROBLEM_SESSION_ENRICHMENT",
                            "FAILED_RETRYABLE", null, "5".repeat(64), 0, 0, 0, 0))
                    .isInstanceOf(PSQLException.class);
            assertThatThrownBy(() -> insertDerivation(connection, userId, problemSessionId, "PROBLEM_SESSION_ENRICHMENT",
                            "FAILED_RETRYABLE", "PROVIDER_TIMEOUT", "6".repeat(64), -1, 0, 0, 0))
                    .isInstanceOf(PSQLException.class);
            assertThatThrownBy(() -> insertDerivation(connection, userId, problemSessionId, "PROBLEM_SESSION_ENRICHMENT",
                            "FAILED_RETRYABLE", "PROVIDER_TIMEOUT", "7".repeat(64), 1, 1, 1, -1))
                    .isInstanceOf(PSQLException.class);
        }
    }

    @Test
    void enforcesSuccessfulDerivationIdempotencyFingerprint() throws SQLException {
        try (var connection = connection()) {
            var userId = insertUser(connection, "ai-derivation-idempotency@example.com");
            var problemSessionId = UUID.randomUUID();
            insertSolvedProblemSession(connection, userId, problemSessionId);
            var fingerprint = "8".repeat(64);

            insertSuccessfulDerivation(connection, userId, problemSessionId, fingerprint);

            assertThatThrownBy(() -> insertSuccessfulDerivation(connection, userId, problemSessionId, fingerprint))
                    .isInstanceOf(PSQLException.class);

            insertDerivation(
                    connection,
                    userId,
                    problemSessionId,
                    "PROBLEM_SESSION_ENRICHMENT",
                    "FAILED_RETRYABLE",
                    "PROVIDER_TIMEOUT",
                    fingerprint,
                    1,
                    0,
                    1,
                    0);
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
                            'Spring Security CORS not working Allowed localhost in CorsConfiguration.'
                        )
                        """)) {
            statement.setObject(1, problemSessionId);
            statement.setObject(2, userId);
            statement.executeUpdate();
        }
    }

    private static void insertSuccessfulDerivation(Connection connection, UUID userId, UUID problemSessionId, String fingerprint)
            throws SQLException {
        insertDerivation(
                connection,
                userId,
                problemSessionId,
                "PROBLEM_SESSION_ENRICHMENT",
                "SUCCEEDED",
                null,
                fingerprint,
                12,
                7,
                19,
                0);
    }

    private static void insertDerivation(
            Connection connection,
            UUID userId,
            UUID problemSessionId,
            String derivationType,
            String status,
            String safeFailureCode,
            String inputFingerprint,
            long inputTokens,
            long outputTokens,
            long totalTokens,
            double estimatedCost)
            throws SQLException {
        try (var statement =
                connection.prepareStatement(
                        """
                        insert into ai_derivations (
                            id,
                            user_id,
                            problem_session_id,
                            derivation_type,
                            provider,
                            model,
                            prompt_key,
                            prompt_version,
                            prompt_hash,
                            output_schema_hash,
                            policy_version,
                            input_fingerprint,
                            status,
                            safe_failure_code,
                            generated_summary,
                            technologies,
                            frameworks,
                            error_phrases,
                            problem_type,
                            input_tokens,
                            output_tokens,
                            total_tokens,
                            estimated_cost,
                            completed_at
                        ) values (
                            ?, ?, ?, ?, 'mock', 'trail-mock-enrichment',
                            'problem-session.enrichment', 1, ?, ?, 'ai-redaction-v1',
                            ?, ?, ?, 'Allowed localhost in CorsConfiguration.',
                            array['Java']::text[], array['Spring Security']::text[],
                            array['CORS not working']::text[], 'configuration',
                            ?, ?, ?, ?,
                            case
                                when ? in ('SUCCEEDED', 'FAILED_TERMINAL', 'REFUSED', 'INVALID_OUTPUT')
                                    then '2026-07-13T09:01:00Z'::timestamptz
                                else null
                            end
                        )
                        """)) {
            statement.setObject(1, UUID.randomUUID());
            statement.setObject(2, userId);
            statement.setObject(3, problemSessionId);
            statement.setString(4, derivationType);
            statement.setString(5, SHA_256);
            statement.setString(6, SHA_256);
            statement.setString(7, inputFingerprint);
            statement.setString(8, status);
            statement.setString(9, safeFailureCode);
            statement.setLong(10, inputTokens);
            statement.setLong(11, outputTokens);
            statement.setLong(12, totalTokens);
            statement.setDouble(13, estimatedCost);
            statement.setString(14, status);
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
}
