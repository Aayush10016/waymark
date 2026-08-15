package io.github.aayush10016.trail.database;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.aayush10016.trail.capture.adapter.out.persistence.JdbcCaptureRetentionRepository;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
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
class DatabaseGrowthRetentionPerformanceFixtureTest {

    private static final String POSTGRES_PGVECTOR_IMAGE =
            "pgvector/pgvector:0.8.2-pg17@sha256:feb68f4f15446397d8cac7f4fe48fe4586de83160d1fc48b46283312d1a33966";
    private static final Instant NOW = Instant.parse("2026-07-21T10:00:00Z");
    private static final Duration FIXTURE_QUERY_BUDGET = Duration.ofSeconds(5);

    @Container
    static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>(
                            DockerImageName.parse(POSTGRES_PGVECTOR_IMAGE)
                                    .asCompatibleSubstituteFor("postgres"))
                    .withDatabaseName("trail_database_growth_fixture_test")
                    .withUsername("trail")
                    .withPassword("trail_local_password");

    private JdbcTemplate jdbcTemplate;
    private JdbcCaptureRetentionRepository retentionRepository;
    private UUID userId;

    @BeforeEach
    void migrateAndSeedGrowthFixture() {
        var flyway =
                Flyway.configure()
                        .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                        .locations("classpath:db/migration")
                        .cleanDisabled(false)
                        .load();
        flyway.clean();
        flyway.migrate();

        jdbcTemplate = new JdbcTemplate(dataSource());
        retentionRepository = new JdbcCaptureRetentionRepository(jdbcTemplate);
        userId = insertUser("growth-fixture@example.com");
        seedCaptureEvents(360);
        seedBackgroundJobs(80);
    }

    @Test
    void retentionCleanupUsesBoundedBatchesIndexPlanVacuumAndShortLocks() throws SQLException {
        assertThat(countRows("capture_events")).isEqualTo(360);
        var plan = explainRetentionCandidatePlan();

        assertThat(plan).contains("idx_capture_events_retention_expires");
        assertThat(measured(() -> retentionRepository.expireRawCaptureEvents(NOW, 50)))
                .satisfies(result -> {
                    assertThat(result.value()).isEqualTo(50);
                    assertThat(result.elapsed()).isLessThan(FIXTURE_QUERY_BUDGET);
                });
        assertThat(countRows("capture_events")).isEqualTo(310);

        try (var locked = lockOneExpiredCaptureEvent()) {
            assertThat(locked.isClosed()).isFalse();
            assertThat(measured(() -> retentionRepository.expireRawCaptureEvents(NOW, 500)).elapsed())
                    .isLessThan(FIXTURE_QUERY_BUDGET);
            assertThat(countRows("capture_events")).isEqualTo(2);
        }

        assertThat(retentionRepository.expireRawCaptureEvents(NOW, 500)).isEqualTo(1);
        jdbcTemplate.execute("vacuum analyze capture_events");
        assertThat(countRows("capture_events")).isEqualTo(1);
    }

    @Test
    void backgroundJobTableGrowthUsesRetryClaimIndexAndDeterministicOrdering() {
        var plan = explainReadyJobClaimPlan();

        assertThat(plan).contains("idx_background_jobs_retry_claim");
        assertThat(jdbcTemplate.queryForList(
                        """
                        select aggregate_id
                        from background_jobs
                        where state = 'FAILED_RETRYABLE'
                            and next_attempt_at <= ?::timestamptz
                        order by next_attempt_at, created_at, id
                        limit 5
                        """,
                        UUID.class,
                        NOW.toString()))
                .hasSize(5);
    }

    private String explainRetentionCandidatePlan() {
        jdbcTemplate.execute("set enable_seqscan = off");
        return String.join("\n", jdbcTemplate.queryForList(
                """
                explain
                select id
                from capture_events
                where retention_expires_at <= ?::timestamptz
                    and deleted_at is null
                order by retention_expires_at, id
                limit 50
                for update skip locked
                """,
                String.class,
                NOW.toString()));
    }

    private String explainReadyJobClaimPlan() {
        jdbcTemplate.execute("set enable_seqscan = off");
        return String.join("\n", jdbcTemplate.queryForList(
                """
                explain
                select id
                from background_jobs
                where state = 'FAILED_RETRYABLE'
                    and next_attempt_at <= ?::timestamptz
                order by next_attempt_at, created_at, id
                limit 5
                for update skip locked
                """,
                String.class,
                NOW.toString()));
    }

    private Measured<Integer> measured(ThrowingOperation operation) throws SQLException {
        var started = System.nanoTime();
        var value = operation.execute();
        return new Measured<>(value, Duration.ofNanos(System.nanoTime() - started));
    }

    private UUID insertUser(String email) {
        var id = UUID.randomUUID();
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
                id,
                email,
                email,
                "$argon2id$v=19$m=65536,t=3,p=2$synthetic$safehash",
                "m=65536,t=3,p=2");
        return id;
    }

    private void seedCaptureEvents(int count) {
        for (var index = 0; index < count; index++) {
            var expired = index < count - 1;
            jdbcTemplate.update(
                    """
                    insert into capture_events (
                        id,
                        user_id,
                        client_event_id,
                        client_sequence,
                        client_instance_id,
                        source,
                        resource_type,
                        sanitized_url,
                        canonical_url_hash,
                        hostname,
                        title,
                        policy_version,
                        occurred_at,
                        retention_expires_at
                    ) values (
                        ?, ?, ?, ?, 'extension-instance', 'WEB_NAVIGATION_COMMITTED',
                        'DOCUMENTATION', ?, decode(repeat('22', 32), 'hex'), 'docs.example.test',
                        'Docs', 'url-query-sanitation-v1', ?::timestamptz, ?::timestamptz
                    )
                    """,
                    UUID.randomUUID(),
                    userId,
                    "growth-event-" + index,
                    index,
                    "https://docs.example.test/problem/" + index,
                    NOW.minusSeconds(index + 1).toString(),
                    expired ? NOW.minusSeconds(index).toString() : NOW.plusSeconds(86_400).toString());
        }
    }

    private void seedBackgroundJobs(int count) {
        for (var index = 0; index < count; index++) {
            jdbcTemplate.update(
                    """
                    insert into background_jobs (
                        id,
                        user_id,
                        aggregate_type,
                        aggregate_id,
                        job_type,
                        state,
                        attempts,
                        next_attempt_at,
                        payload,
                        created_at,
                        updated_at
                    ) values (
                        ?, ?, 'PROBLEM_SESSION', ?, 'PROBLEM_SESSION_ENRICHMENT',
                        'FAILED_RETRYABLE', 1, ?::timestamptz, '{}'::jsonb,
                        ?::timestamptz, ?::timestamptz
                    )
                    """,
                    UUID.randomUUID(),
                    userId,
                    UUID.randomUUID(),
                    NOW.minusSeconds(index + 1).toString(),
                    NOW.minusSeconds(index + 2).toString(),
                    NOW.minusSeconds(index + 2).toString());
        }
    }

    private int countRows(String tableName) {
        return jdbcTemplate.queryForObject("select count(*) from " + tableName, Integer.class);
    }

    private Connection lockOneExpiredCaptureEvent() throws SQLException {
        var connection = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        connection.setAutoCommit(false);
        try (var statement = connection.prepareStatement(
                """
                select id
                from capture_events
                where retention_expires_at <= ?::timestamptz
                order by retention_expires_at, id
                limit 1
                for update
                """)) {
            statement.setString(1, NOW.toString());
            statement.executeQuery();
        }
        return connection;
    }

    private static PGSimpleDataSource dataSource() {
        var dataSource = new PGSimpleDataSource();
        dataSource.setUrl(postgres.getJdbcUrl());
        dataSource.setUser(postgres.getUsername());
        dataSource.setPassword(postgres.getPassword());
        return dataSource;
    }

    private record Measured<T>(T value, Duration elapsed) {}

    @FunctionalInterface
    private interface ThrowingOperation {
        int execute() throws SQLException;
    }
}
