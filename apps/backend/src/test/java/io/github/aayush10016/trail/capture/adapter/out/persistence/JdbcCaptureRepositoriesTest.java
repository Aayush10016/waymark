package io.github.aayush10016.trail.capture.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.aayush10016.trail.capture.domain.model.CaptureEventToPersist;
import io.github.aayush10016.trail.capture.domain.port.CaptureEventRepository;
import io.github.aayush10016.trail.identity.domain.model.AuthenticatedPrincipal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
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
class JdbcCaptureRepositoriesTest {

    private static final String POSTGRES_PGVECTOR_IMAGE =
            "pgvector/pgvector:0.8.2-pg17@sha256:feb68f4f15446397d8cac7f4fe48fe4586de83160d1fc48b46283312d1a33966";

    @Container
    static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>(
                            DockerImageName.parse(POSTGRES_PGVECTOR_IMAGE)
                                    .asCompatibleSubstituteFor("postgres"))
                    .withDatabaseName("trail_capture_repository_test")
                    .withUsername("trail")
                    .withPassword("trail_local_password");

    private JdbcTemplate jdbcTemplate;
    private JdbcCaptureConsentRepository consentRepository;
    private JdbcCaptureEventRepository eventRepository;
    private JdbcCaptureRetentionRepository retentionRepository;

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

        var dataSource = new PGSimpleDataSource();
        dataSource.setUrl(postgres.getJdbcUrl());
        dataSource.setUser(postgres.getUsername());
        dataSource.setPassword(postgres.getPassword());

        jdbcTemplate = new JdbcTemplate(dataSource);
        consentRepository = new JdbcCaptureConsentRepository(jdbcTemplate);
        eventRepository = new JdbcCaptureEventRepository(jdbcTemplate);
        retentionRepository = new JdbcCaptureRetentionRepository(jdbcTemplate);
    }

    @Test
    void readsLatestConsentDecisionForUser() {
        var principal = new AuthenticatedPrincipal(insertUser("consent-reader@example.com"));

        insertConsent(principal.userId(), "GRANTED");
        assertThat(consentRepository.hasActiveCaptureConsent(principal)).isTrue();

        insertConsent(principal.userId(), "WITHDRAWN");
        assertThat(consentRepository.hasActiveCaptureConsent(principal)).isFalse();
    }

    @Test
    void recordsCaptureConsentGrantAndWithdrawal() {
        var principal = new AuthenticatedPrincipal(insertUser("consent-writer@example.com"));

        consentRepository.recordGrantedCaptureConsent(
                principal, "capture-consent-v1", Instant.parse("2026-07-13T08:00:00Z"));
        assertThat(consentRepository.hasActiveCaptureConsent(principal)).isTrue();
        assertThat(consentRepository.latestCapturePolicyVersion(principal)).contains("capture-consent-v1");

        consentRepository.recordWithdrawnCaptureConsent(
                principal, "capture-consent-v1", Instant.parse("2026-07-13T09:00:00Z"));
        assertThat(consentRepository.hasActiveCaptureConsent(principal)).isFalse();
        assertThat(consentDecisionRows(principal.userId())).containsExactly("GRANTED", "WITHDRAWN");
    }

    @Test
    void storesCaptureEventsIdempotentlyPerUser() {
        var firstPrincipal = new AuthenticatedPrincipal(insertUser("first-repository@example.com"));
        var secondPrincipal = new AuthenticatedPrincipal(insertUser("second-repository@example.com"));
        var event = event("client-event-1");

        assertThat(eventRepository.storeIfAbsent(firstPrincipal, event))
                .isEqualTo(CaptureEventRepository.StoreCaptureEventResult.INSERTED);
        assertThat(eventRepository.storeIfAbsent(firstPrincipal, event))
                .isEqualTo(CaptureEventRepository.StoreCaptureEventResult.DUPLICATE);
        assertThat(eventRepository.storeIfAbsent(secondPrincipal, event))
                .isEqualTo(CaptureEventRepository.StoreCaptureEventResult.INSERTED);

        assertThat(countCaptureEvents()).isEqualTo(2);
    }

    @Test
    void persistsSanitizedCaptureFieldsAndRetention() {
        var principal = new AuthenticatedPrincipal(insertUser("persisted-fields@example.com"));

        eventRepository.storeIfAbsent(principal, event("client-event-2"));

        var stored = jdbcTemplate.queryForMap(
                "select sanitized_url, hostname, policy_version, retention_expires_at from capture_events where user_id = ?",
                principal.userId());
        assertThat(stored.get("sanitized_url")).isEqualTo("https://example.test/docs?topic=cors");
        assertThat(stored.get("hostname")).isEqualTo("example.test");
        assertThat(stored.get("policy_version")).isEqualTo("url-query-sanitation-v1");
        assertThat(stored.get("retention_expires_at").toString()).contains("2026-07-20");
    }

    @Test
    void expiresRawCaptureEventsInChunksAndLeavesFreshRows() {
        var principal = new AuthenticatedPrincipal(insertUser("retention-chunking@example.com"));
        eventRepository.storeIfAbsent(
                principal,
                event(
                        "expired-event-1",
                        Instant.parse("2026-07-13T08:00:00Z"),
                        Instant.parse("2026-07-14T08:00:00Z")));
        eventRepository.storeIfAbsent(
                principal,
                event(
                        "expired-event-2",
                        Instant.parse("2026-07-13T09:00:00Z"),
                        Instant.parse("2026-07-14T09:00:00Z")));
        eventRepository.storeIfAbsent(
                principal,
                event(
                        "expired-event-3",
                        Instant.parse("2026-07-13T10:00:00Z"),
                        Instant.parse("2026-07-14T10:00:00Z")));
        eventRepository.storeIfAbsent(
                principal,
                event(
                        "fresh-event",
                        Instant.parse("2026-07-20T08:00:00Z"),
                        Instant.parse("2026-07-27T08:00:00Z")));

        var cutoff = Instant.parse("2026-07-20T08:00:00Z");

        assertThat(retentionRepository.expireRawCaptureEvents(cutoff, 2)).isEqualTo(2);
        assertThat(clientEventIds()).containsExactlyInAnyOrder("expired-event-3", "fresh-event");
        assertThat(retentionRepository.expireRawCaptureEvents(cutoff, 2)).isEqualTo(1);
        assertThat(clientEventIds()).containsExactly("fresh-event");
        assertThat(retentionRepository.expireRawCaptureEvents(cutoff, 2)).isZero();
        assertThat(clientEventIds()).containsExactly("fresh-event");
    }

    @Test
    void skipsRowsLockedByAnotherRetentionWorker() throws SQLException {
        var principal = new AuthenticatedPrincipal(insertUser("retention-locks@example.com"));
        eventRepository.storeIfAbsent(
                principal,
                event(
                        "locked-expired-event",
                        Instant.parse("2026-07-13T08:00:00Z"),
                        Instant.parse("2026-07-14T08:00:00Z")));
        eventRepository.storeIfAbsent(
                principal,
                event(
                        "available-expired-event",
                        Instant.parse("2026-07-13T09:00:00Z"),
                        Instant.parse("2026-07-14T09:00:00Z")));

        try (var connection = lockedEventConnection("locked-expired-event")) {
            assertThat(connection.isClosed()).isFalse();
            assertThat(retentionRepository.expireRawCaptureEvents(Instant.parse("2026-07-20T08:00:00Z"), 10))
                    .isEqualTo(1);
            assertThat(clientEventIds()).containsExactly("locked-expired-event");
        }

        assertThat(retentionRepository.expireRawCaptureEvents(Instant.parse("2026-07-20T08:00:00Z"), 10))
                .isEqualTo(1);
        assertThat(clientEventIds()).isEmpty();
    }

    private UUID insertUser(String email) {
        var userId = UUID.randomUUID();
        jdbcTemplate.update(
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
                """,
                userId,
                email,
                email,
                "$argon2id$v=19$m=65536,t=3,p=2$synthetic$safehash",
                "m=65536,t=3,p=2");
        return userId;
    }

    private void insertConsent(UUID userId, String decision) {
        jdbcTemplate.update(
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
                """,
                UUID.randomUUID(),
                userId,
                decision,
                "GRANTED".equals(decision) ? "2026-07-13T08:00:00Z" : null,
                "WITHDRAWN".equals(decision) ? "2026-07-13T09:00:00Z" : null);
    }

    private int countCaptureEvents() {
        return jdbcTemplate.queryForObject("select count(*) from capture_events", Integer.class);
    }

    private List<String> consentDecisionRows(UUID userId) {
        return jdbcTemplate.queryForList(
                "select decision from consent_grants where user_id = ? order by created_at asc, id asc",
                String.class,
                userId);
    }

    private CaptureEventToPersist event(String clientEventId) {
        return event(
                clientEventId,
                Instant.parse("2026-07-13T08:00:00Z"),
                Instant.parse("2026-07-20T08:00:00Z"));
    }

    private CaptureEventToPersist event(String clientEventId, Instant occurredAt, Instant retentionExpiresAt) {
        return new CaptureEventToPersist(
                clientEventId,
                1L,
                "extension-instance-1",
                "tab-session-1",
                null,
                null,
                "WEB_NAVIGATION_COMMITTED",
                "DOCUMENTATION",
                "link",
                "https://example.test/docs?topic=cors",
                "example.test",
                "Example docs",
                null,
                null,
                List.of("utm_source"),
                List.of("from_address_bar"),
                "url-query-sanitation-v1",
                100D,
                occurredAt,
                retentionExpiresAt);
    }

    private List<String> clientEventIds() {
        return jdbcTemplate.queryForList(
                "select client_event_id from capture_events order by client_event_id",
                String.class);
    }

    private Connection lockedEventConnection(String clientEventId) throws SQLException {
        var connection = DriverManager.getConnection(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        connection.setAutoCommit(false);
        try (var statement =
                connection.prepareStatement("select id from capture_events where client_event_id = ? for update")) {
            statement.setString(1, clientEventId);
            statement.executeQuery();
        }
        return connection;
    }
}
