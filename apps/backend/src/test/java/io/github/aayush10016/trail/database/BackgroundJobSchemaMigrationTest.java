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
class BackgroundJobSchemaMigrationTest {

    private static final String POSTGRES_PGVECTOR_IMAGE =
            "pgvector/pgvector:0.8.2-pg17@sha256:feb68f4f15446397d8cac7f4fe48fe4586de83160d1fc48b46283312d1a33966";

    @Container
    static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>(
                            DockerImageName.parse(POSTGRES_PGVECTOR_IMAGE)
                                    .asCompatibleSubstituteFor("postgres"))
                    .withDatabaseName("trail_background_job_schema_test")
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
    void createsBackgroundJobLeasingColumnsAndIndexes() throws SQLException {
        try (var connection = connection()) {
            assertThat(columnNames(connection, "background_jobs"))
                    .contains("lease_owner", "lease_expires_at");
            assertThat(indexNames(connection))
                    .contains(
                            "idx_background_jobs_ready_claim",
                            "idx_background_jobs_retry_claim",
                            "idx_background_jobs_expired_lease",
                            "uq_background_jobs_user_type_aggregate");
        }
    }

    @Test
    void enforcesLeaseStateConsistency() throws SQLException {
        try (var connection = connection()) {
            var userId = insertUser(connection, "background-job-schema@example.com");
            insertReadyJob(connection, userId, UUID.randomUUID());
            insertLeasedJob(connection, userId, UUID.randomUUID());

            assertThatThrownBy(() -> insertLeasedJobWithoutOwner(connection, userId, UUID.randomUUID()))
                    .isInstanceOf(PSQLException.class);
            assertThatThrownBy(() -> insertReadyJobWithOwner(connection, userId, UUID.randomUUID()))
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

    private static void insertReadyJob(Connection connection, UUID userId, UUID aggregateId) throws SQLException {
        insertJob(connection, userId, aggregateId, "READY", null, null);
    }

    private static void insertLeasedJob(Connection connection, UUID userId, UUID aggregateId) throws SQLException {
        insertJob(connection, userId, aggregateId, "LEASED", "worker-a", "2026-07-16T08:05:00Z");
    }

    private static void insertLeasedJobWithoutOwner(Connection connection, UUID userId, UUID aggregateId)
            throws SQLException {
        insertJob(connection, userId, aggregateId, "LEASED", null, "2026-07-16T08:05:00Z");
    }

    private static void insertReadyJobWithOwner(Connection connection, UUID userId, UUID aggregateId)
            throws SQLException {
        insertJob(connection, userId, aggregateId, "READY", "worker-a", "2026-07-16T08:05:00Z");
    }

    private static void insertJob(
            Connection connection,
            UUID userId,
            UUID aggregateId,
            String state,
            String leaseOwner,
            String leaseExpiresAt)
            throws SQLException {
        try (var statement =
                connection.prepareStatement(
                        """
                        insert into background_jobs (
                            id,
                            user_id,
                            aggregate_type,
                            aggregate_id,
                            job_type,
                            state,
                            next_attempt_at,
                            lease_owner,
                            lease_expires_at,
                            payload
                        ) values (
                            ?, ?, 'PROBLEM_SESSION', ?, 'PROBLEM_SESSION_ENRICHMENT',
                            ?, '2026-07-16T08:00:00Z'::timestamptz, ?, ?::timestamptz, '{}'::jsonb
                        )
                        """)) {
            statement.setObject(1, UUID.randomUUID());
            statement.setObject(2, userId);
            statement.setObject(3, aggregateId);
            statement.setString(4, state);
            statement.setString(5, leaseOwner);
            statement.setString(6, leaseExpiresAt);
            statement.executeUpdate();
        }
    }

    private static List<String> columnNames(Connection connection, String tableName) throws SQLException {
        try (var statement =
                        connection.prepareStatement(
                                """
                                select column_name
                                from information_schema.columns
                                where table_schema = 'public'
                                    and table_name = ?
                                order by column_name
                                """)) {
            statement.setString(1, tableName);
            try (var resultSet = statement.executeQuery()) {
                var names = new java.util.ArrayList<String>();
                while (resultSet.next()) {
                    names.add(resultSet.getString("column_name"));
                }
                return names;
            }
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
