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
class IdentitySchemaMigrationTest {

    private static final String POSTGRES_PGVECTOR_IMAGE =
            "pgvector/pgvector:0.8.2-pg17@sha256:feb68f4f15446397d8cac7f4fe48fe4586de83160d1fc48b46283312d1a33966";

    @Container
    static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>(
                            DockerImageName.parse(POSTGRES_PGVECTOR_IMAGE)
                                    .asCompatibleSubstituteFor("postgres"))
                    .withDatabaseName("trail_identity_test")
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
    void createsIdentityTablesAndIndexes() throws SQLException {
        try (var connection = connection()) {
            assertThat(tableNames(connection))
                    .contains("users", "auth_sessions", "email_action_tokens", "flyway_schema_history");

            assertThat(indexNames(connection))
                    .contains(
                            "uq_users_email_normalized",
                            "idx_users_active_lock",
                            "idx_users_delete_requested",
                            "idx_auth_sessions_session_family",
                            "uq_auth_sessions_active_refresh_token_hash",
                            "idx_auth_sessions_user_active_expiry",
                            "idx_auth_sessions_user_created",
                            "uq_email_action_tokens_active_token_hash",
                            "idx_email_action_tokens_active_expiry",
                            "idx_email_action_tokens_user_purpose_active");
        }
    }

    @Test
    void enforcesUserConstraints() throws SQLException {
        try (var connection = connection()) {
            var userId = insertUser(connection, "developer@example.com", "developer@example.com", "ACTIVE");

            assertThat(userId).isNotNull();

            assertThatThrownBy(() -> insertUser(connection, "developer@example.com", "Developer@example.com", "ACTIVE"))
                    .isInstanceOf(PSQLException.class);

            assertThatThrownBy(() -> insertUser(connection, "MixedCase@example.com", "MixedCase@example.com", "ACTIVE"))
                    .isInstanceOf(PSQLException.class);

            assertThatThrownBy(() -> insertUser(connection, "invalid-status@example.com", "invalid-status@example.com", "UNKNOWN"))
                    .isInstanceOf(PSQLException.class);
        }
    }

    @Test
    void enforcesAuthSessionHashOnlyStorageAndActiveTokenUniqueness() throws SQLException {
        try (var connection = connection()) {
            var userId = insertUser(connection, "session@example.com", "session@example.com", "ACTIVE");
            var tokenHash = hashBytes(1);

            insertAuthSession(connection, userId, tokenHash, null);

            assertThatThrownBy(() -> insertAuthSession(connection, userId, tokenHash, null))
                    .isInstanceOf(PSQLException.class);

            insertAuthSession(connection, userId, tokenHash, OffsetDateTime.now().plusMinutes(1));

            assertThat(tokenColumnNames(connection, "auth_sessions"))
                    .containsExactly("refresh_token_hash", "refresh_token_hash_algorithm")
                    .allMatch(columnName -> columnName.contains("hash"));
            assertThat(columnType(connection, "auth_sessions", "refresh_token_hash")).isEqualTo("bytea");
        }
    }

    @Test
    void enforcesEmailActionTokenHashOnlyStoragePurposeAndActiveTokenUniqueness() throws SQLException {
        try (var connection = connection()) {
            var userId = insertUser(connection, "email-token@example.com", "email-token@example.com", "PENDING_VERIFICATION");
            var tokenHash = hashBytes(2);

            insertEmailActionToken(connection, userId, "VERIFY_EMAIL", tokenHash, null);

            assertThatThrownBy(() -> insertEmailActionToken(connection, userId, "VERIFY_EMAIL", tokenHash, null))
                    .isInstanceOf(PSQLException.class);

            markEmailActionTokenConsumed(connection, tokenHash);
            insertEmailActionToken(connection, userId, "VERIFY_EMAIL", tokenHash, null);

            assertThatThrownBy(() -> insertEmailActionToken(connection, userId, "UNKNOWN", hashBytes(3), null))
                    .isInstanceOf(PSQLException.class);

            assertThat(tokenColumnNames(connection, "email_action_tokens"))
                    .containsExactly("token_hash", "token_hash_algorithm")
                    .allMatch(columnName -> columnName.contains("hash"));
            assertThat(columnType(connection, "email_action_tokens", "token_hash")).isEqualTo("bytea");
        }
    }

    private static Connection connection() throws SQLException {
        return DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
    }

    private static UUID insertUser(Connection connection, String normalizedEmail, String displayEmail, String status)
            throws SQLException {
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
                        ) values (?, ?, ?, ?, 'ARGON2ID', ?, 1, ?)
                        """)) {
            statement.setObject(1, userId);
            statement.setString(2, normalizedEmail);
            statement.setString(3, displayEmail);
            statement.setString(4, "$argon2id$v=19$m=65536,t=3,p=2$synthetic$safehash");
            statement.setString(5, "m=65536,t=3,p=2");
            statement.setString(6, status);
            statement.executeUpdate();
        }
        return userId;
    }

    private static void insertAuthSession(
            Connection connection, UUID userId, byte[] tokenHash, OffsetDateTime revokedAt) throws SQLException {
        try (var statement =
                connection.prepareStatement(
                        """
                        insert into auth_sessions (
                            id,
                            session_family_id,
                            user_id,
                            refresh_token_hash,
                            refresh_token_hash_algorithm,
                            refresh_rotation_counter,
                            client_type,
                            client_version,
                            expires_at,
                            revoked_at
                        ) values (?, ?, ?, ?, 'SHA-256', 0, 'CHROME_EXTENSION', '0.1.0', now() + interval '30 days', ?)
                        """)) {
            statement.setObject(1, UUID.randomUUID());
            statement.setObject(2, UUID.randomUUID());
            statement.setObject(3, userId);
            statement.setBytes(4, tokenHash);
            statement.setObject(5, revokedAt);
            statement.executeUpdate();
        }
    }

    private static void insertEmailActionToken(
            Connection connection, UUID userId, String purpose, byte[] tokenHash, OffsetDateTime consumedAt)
            throws SQLException {
        try (var statement =
                connection.prepareStatement(
                        """
                        insert into email_action_tokens (
                            id,
                            user_id,
                            purpose,
                            token_hash,
                            token_hash_algorithm,
                            expires_at,
                            consumed_at
                        ) values (?, ?, ?, ?, 'SHA-256', now() + interval '1 hour', ?)
                        """)) {
            statement.setObject(1, UUID.randomUUID());
            statement.setObject(2, userId);
            statement.setString(3, purpose);
            statement.setBytes(4, tokenHash);
            statement.setObject(5, consumedAt);
            statement.executeUpdate();
        }
    }

    private static void markEmailActionTokenConsumed(Connection connection, byte[] tokenHash) throws SQLException {
        try (PreparedStatement statement =
                connection.prepareStatement("update email_action_tokens set consumed_at = now() where token_hash = ?")) {
            statement.setBytes(1, tokenHash);
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

    private static List<String> tokenColumnNames(Connection connection, String tableName) throws SQLException {
        try (PreparedStatement statement =
                connection.prepareStatement(
                        """
                        select column_name
                        from information_schema.columns
                        where table_schema = 'public'
                            and table_name = ?
                            and column_name like '%token%'
                        order by ordinal_position
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

    private static String columnType(Connection connection, String tableName, String columnName) throws SQLException {
        try (PreparedStatement statement =
                connection.prepareStatement(
                        """
                        select data_type
                        from information_schema.columns
                        where table_schema = 'public'
                            and table_name = ?
                            and column_name = ?
                        """)) {
            statement.setString(1, tableName);
            statement.setString(2, columnName);
            try (var resultSet = statement.executeQuery()) {
                assertThat(resultSet.next()).isTrue();
                return resultSet.getString("data_type");
            }
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
