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
class CaptureConsentSchemaMigrationTest {

    private static final String POSTGRES_PGVECTOR_IMAGE =
            "pgvector/pgvector:0.8.2-pg17@sha256:feb68f4f15446397d8cac7f4fe48fe4586de83160d1fc48b46283312d1a33966";

    @Container
    static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>(
                            DockerImageName.parse(POSTGRES_PGVECTOR_IMAGE)
                                    .asCompatibleSubstituteFor("postgres"))
                    .withDatabaseName("trail_capture_consent_test")
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
    void createsCaptureAndConsentTablesWithCriticalIndexes() throws SQLException {
        try (var connection = connection()) {
            assertThat(tableNames(connection))
                    .contains("users", "consent_grants", "capture_events", "flyway_schema_history");

            assertThat(indexNames(connection))
                    .contains(
                            "idx_consent_grants_user_created",
                            "idx_consent_grants_user_policy_created",
                            "uq_capture_events_user_client_event_id",
                            "idx_capture_events_user_occurred",
                            "idx_capture_events_user_candidate_occurred",
                            "idx_capture_events_retention_expires");
        }
    }

    @Test
    void enforcesConsentOwnershipScopeAndAppendOnlyEvidenceShape() throws SQLException {
        try (var connection = connection()) {
            var userId = insertUser(connection, "consent@example.com", "consent@example.com", "ACTIVE");

            insertConsentDecision(connection, userId, "GRANTED", "2026-07-13T08:00:00Z", null);
            insertConsentDecision(connection, userId, "WITHDRAWN", null, "2026-07-13T09:00:00Z");

            assertThatThrownBy(() -> insertConsentDecision(connection, UUID.randomUUID(), "GRANTED", "2026-07-13T08:00:00Z", null))
                    .isInstanceOf(PSQLException.class);
            assertThatThrownBy(() -> insertConsentDecision(connection, userId, "UNKNOWN", "2026-07-13T08:00:00Z", null))
                    .isInstanceOf(PSQLException.class);
            assertThatThrownBy(() -> insertConsentDecision(connection, userId, "GRANTED", "2026-07-13T08:00:00Z", "2026-07-13T09:00:00Z"))
                    .isInstanceOf(PSQLException.class);

            assertThat(columnNames(connection, "consent_grants"))
                    .doesNotContain("ip_address", "user_agent");
        }
    }

    @Test
    void enforcesCaptureOwnershipAndUserScopedIdempotency() throws SQLException {
        try (var connection = connection()) {
            var firstUserId = insertUser(connection, "first-capture@example.com", "first-capture@example.com", "ACTIVE");
            var secondUserId = insertUser(connection, "second-capture@example.com", "second-capture@example.com", "ACTIVE");

            insertCaptureEvent(connection, firstUserId, "client-event-1", null, null);

            assertThatThrownBy(() -> insertCaptureEvent(connection, firstUserId, "client-event-1", null, null))
                    .isInstanceOf(PSQLException.class);

            insertCaptureEvent(connection, secondUserId, "client-event-1", null, null);

            assertThatThrownBy(() -> insertCaptureEvent(connection, UUID.randomUUID(), "client-event-2", null, null))
                    .isInstanceOf(PSQLException.class);
        }
    }

    @Test
    void enforcesCaptureRetentionSanitizedUrlAndBoundedContent() throws SQLException {
        try (var connection = connection()) {
            var userId = insertUser(connection, "capture-rules@example.com", "capture-rules@example.com", "ACTIVE");
            var occurredAt = OffsetDateTime.parse("2026-07-13T08:00:00Z");

            insertCaptureEvent(connection, userId, "valid-event", occurredAt.plusDays(7), null);

            assertThatThrownBy(() -> insertCaptureEvent(connection, userId, "bad-retention", occurredAt.minusSeconds(1), null))
                    .isInstanceOf(PSQLException.class);
            assertThatThrownBy(() -> insertCaptureEvent(connection, userId, "bad-hash", occurredAt.plusDays(7), new byte[] {1, 2, 3}))
                    .isInstanceOf(PSQLException.class);
            assertThatThrownBy(() -> insertCaptureEventWithUrl(connection, userId, "bad-url", "chrome://extensions"))
                    .isInstanceOf(PSQLException.class);
            assertThatThrownBy(() -> insertCaptureEventWithTitle(connection, userId, "bad-title", "x".repeat(121)))
                    .isInstanceOf(PSQLException.class);
        }
    }

    @Test
    void linksCaptureCandidatesToProblemSessionsAfterProblemSessionMigration() throws SQLException {
        try (var connection = connection()) {
            assertThat(columnNames(connection, "capture_events")).contains("candidate_problem_session_id");
            assertThat(foreignKeyColumnNames(connection, "capture_events")).contains("candidate_problem_session_id");
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

    private static void insertConsentDecision(
            Connection connection, UUID userId, String decision, String grantedAt, String withdrawnAt)
            throws SQLException {
        try (var statement =
                connection.prepareStatement(
                        """
                        insert into consent_grants (
                            id,
                            user_id,
                            policy_version,
                            capture_scope,
                            ai_scope,
                            decision,
                            granted_at,
                            withdrawn_at
                        ) values (?, ?, 'capture-consent-v1', 'BROWSER_NAVIGATION_METADATA', 'NONE', ?, ?::timestamptz, ?::timestamptz)
                        """)) {
            statement.setObject(1, UUID.randomUUID());
            statement.setObject(2, userId);
            statement.setString(3, decision);
            statement.setString(4, grantedAt);
            statement.setString(5, withdrawnAt);
            statement.executeUpdate();
        }
    }

    private static void insertCaptureEvent(
            Connection connection, UUID userId, String clientEventId, OffsetDateTime retentionExpiresAt, byte[] urlHash)
            throws SQLException {
        insertCaptureEvent(
                connection,
                userId,
                clientEventId,
                "https://docs.spring.io/spring-security/reference/servlet/integrations/cors.html",
                "CORS :: Spring Security",
                retentionExpiresAt,
                urlHash);
    }

    private static void insertCaptureEventWithUrl(Connection connection, UUID userId, String clientEventId, String url)
            throws SQLException {
        insertCaptureEvent(connection, userId, clientEventId, url, "CORS :: Spring Security", null, null);
    }

    private static void insertCaptureEventWithTitle(Connection connection, UUID userId, String clientEventId, String title)
            throws SQLException {
        insertCaptureEvent(
                connection,
                userId,
                clientEventId,
                "https://docs.spring.io/spring-security/reference/servlet/integrations/cors.html",
                title,
                null,
                null);
    }

    private static void insertCaptureEvent(
            Connection connection,
            UUID userId,
            String clientEventId,
            String sanitizedUrl,
            String title,
            OffsetDateTime retentionExpiresAt,
            byte[] urlHash)
            throws SQLException {
        var occurredAt = OffsetDateTime.parse("2026-07-13T08:00:00Z");
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
                            parent_client_event_id,
                            source,
                            resource_type,
                            navigation_type,
                            sanitized_url,
                            canonical_url_hash,
                            hostname,
                            title,
                            search_engine,
                            search_query,
                            removed_query_parameters,
                            transition_qualifiers,
                            policy_version,
                            browser_event_time_ms,
                            occurred_at,
                            candidate_problem_session_id,
                            quality_flags,
                            retention_expires_at
                        ) values (
                            ?, ?, ?, 1, 'extension-instance-1', 'tab-session-1', null,
                            'WEB_NAVIGATION_COMMITTED', 'DOCUMENTATION', 'link',
                            ?, ?, 'docs.spring.io', ?, null, null,
                            array['utm_source']::text[], array['from_address_bar']::text[],
                            'url-query-sanitation-v1', 100, ?, null, array[]::text[], ?
                        )
                        """)) {
            statement.setObject(1, UUID.randomUUID());
            statement.setObject(2, userId);
            statement.setString(3, clientEventId);
            statement.setString(4, sanitizedUrl);
            statement.setBytes(5, urlHash == null ? hashBytes(1) : urlHash);
            statement.setString(6, title);
            statement.setObject(7, occurredAt);
            statement.setObject(8, retentionExpiresAt == null ? occurredAt.plusDays(7) : retentionExpiresAt);
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

    private static List<String> columnNames(Connection connection, String tableName) throws SQLException {
        try (PreparedStatement statement =
                connection.prepareStatement(
                        """
                        select column_name
                        from information_schema.columns
                        where table_schema = 'public'
                            and table_name = ?
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

    private static List<String> foreignKeyColumnNames(Connection connection, String tableName) throws SQLException {
        try (PreparedStatement statement =
                connection.prepareStatement(
                        """
                        select kcu.column_name
                        from information_schema.table_constraints tc
                        join information_schema.key_column_usage kcu
                            on tc.constraint_name = kcu.constraint_name
                            and tc.table_schema = kcu.table_schema
                            and tc.table_name = kcu.table_name
                        where tc.constraint_type = 'FOREIGN KEY'
                            and tc.table_schema = 'public'
                            and tc.table_name = ?
                        order by kcu.column_name
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

    private static byte[] hashBytes(int seed) {
        var bytes = new byte[32];
        for (int index = 0; index < bytes.length; index += 1) {
            bytes[index] = (byte) (seed + index);
        }
        return bytes;
    }
}
