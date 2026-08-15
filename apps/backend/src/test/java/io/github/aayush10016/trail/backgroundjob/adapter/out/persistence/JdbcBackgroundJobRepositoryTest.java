package io.github.aayush10016.trail.backgroundjob.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.aayush10016.trail.backgroundjob.domain.model.BackgroundJob;
import io.github.aayush10016.trail.backgroundjob.domain.model.BackgroundJobAggregateType;
import io.github.aayush10016.trail.backgroundjob.domain.model.BackgroundJobState;
import io.github.aayush10016.trail.backgroundjob.domain.model.BackgroundJobType;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Timestamp;
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
class JdbcBackgroundJobRepositoryTest {

    private static final String POSTGRES_PGVECTOR_IMAGE =
            "pgvector/pgvector:0.8.2-pg17@sha256:feb68f4f15446397d8cac7f4fe48fe4586de83160d1fc48b46283312d1a33966";
    private static final Instant NOW = Instant.parse("2026-07-16T08:00:00Z");
    private static final Instant LEASE_EXPIRES_AT = Instant.parse("2026-07-16T08:05:00Z");

    @Container
    static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>(
                            DockerImageName.parse(POSTGRES_PGVECTOR_IMAGE)
                                    .asCompatibleSubstituteFor("postgres"))
                    .withDatabaseName("trail_background_job_repository_test")
                    .withUsername("trail")
                    .withPassword("trail_local_password");

    private JdbcBackgroundJobRepository repository;
    private JdbcTemplate jdbcTemplate;

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
        repository = new JdbcBackgroundJobRepository(jdbcTemplate);
    }

    @Test
    void insertsJobsIdempotentlyByUserTypeAndAggregate() {
        var userId = insertUser("background-dedup@example.com");
        var aggregateId = UUID.randomUUID();
        var job = job(userId, aggregateId, NOW);

        assertThat(repository.insertIfAbsent(job)).isTrue();
        assertThat(repository.insertIfAbsent(job(userId, aggregateId, NOW.plusSeconds(1)))).isFalse();

        assertThat(countJobs()).isEqualTo(1);
    }

    @Test
    void claimsDueReadyJobsInDeterministicOrderWithBoundedLease() {
        var userId = insertUser("background-claim@example.com");
        var firstAggregateId = UUID.randomUUID();
        var secondAggregateId = UUID.randomUUID();
        var futureAggregateId = UUID.randomUUID();
        repository.insertIfAbsent(job(userId, secondAggregateId, NOW.minusSeconds(30)));
        repository.insertIfAbsent(job(userId, firstAggregateId, NOW.minusSeconds(60)));
        repository.insertIfAbsent(job(userId, futureAggregateId, NOW.plusSeconds(60)));

        var claimed = repository.claimReadyJobs(
                BackgroundJobType.PROBLEM_SESSION_ENRICHMENT, "worker-a", NOW, LEASE_EXPIRES_AT, 2);

        assertThat(claimed).extracting(BackgroundJob::aggregateId).containsExactly(firstAggregateId, secondAggregateId);
        assertThat(claimed)
                .allSatisfy(job -> {
                    assertThat(job.state()).isEqualTo(BackgroundJobState.LEASED);
                    assertThat(job.leaseOwner()).isEqualTo("worker-a");
                    assertThat(job.leaseExpiresAt()).isEqualTo(LEASE_EXPIRES_AT);
                    assertThat(job.attempts()).isEqualTo(1);
                });
        assertThat(jobState(futureAggregateId)).isEqualTo("READY");
    }

    @Test
    void reclaimsExpiredLeasesWithoutClaimingActiveLeases() {
        var userId = insertUser("background-expired-lease@example.com");
        var expiredAggregateId = UUID.randomUUID();
        var activeAggregateId = UUID.randomUUID();
        repository.insertIfAbsent(job(userId, expiredAggregateId, NOW.minusSeconds(60)));
        repository.insertIfAbsent(job(userId, activeAggregateId, NOW.minusSeconds(30)));
        repository.claimReadyJobs(BackgroundJobType.PROBLEM_SESSION_ENRICHMENT, "worker-a", NOW, LEASE_EXPIRES_AT, 2);
        expireLease(expiredAggregateId, NOW.minusSeconds(1));

        var reclaimed = repository.claimReadyJobs(
                BackgroundJobType.PROBLEM_SESSION_ENRICHMENT,
                "worker-b",
                NOW.plusSeconds(1),
                LEASE_EXPIRES_AT.plusSeconds(60),
                10);

        assertThat(reclaimed).singleElement().satisfies(job -> {
            assertThat(job.aggregateId()).isEqualTo(expiredAggregateId);
            assertThat(job.leaseOwner()).isEqualTo("worker-b");
            assertThat(job.attempts()).isEqualTo(2);
        });
        assertThat(leaseOwner(activeAggregateId)).isEqualTo("worker-a");
    }

    @Test
    void skipsRowsLockedByAnotherClaimingTransaction() throws SQLException {
        var userId = insertUser("background-skip-locked@example.com");
        var lockedAggregateId = UUID.randomUUID();
        var availableAggregateId = UUID.randomUUID();
        repository.insertIfAbsent(job(userId, lockedAggregateId, NOW.minusSeconds(60)));
        repository.insertIfAbsent(job(userId, availableAggregateId, NOW.minusSeconds(30)));

        try (var lockedConnection = lockJobByAggregateId(lockedAggregateId)) {
            assertThat(lockedConnection.isClosed()).isFalse();

            var claimed = repository.claimReadyJobs(
                    BackgroundJobType.PROBLEM_SESSION_ENRICHMENT, "worker-a", NOW, LEASE_EXPIRES_AT, 2);

            assertThat(claimed).extracting(BackgroundJob::aggregateId).containsExactly(availableAggregateId);
            assertThat(jobState(lockedAggregateId)).isEqualTo("READY");
        }
    }

    @Test
    void validatesLeaseClaimInputsBeforeQuerying() {
        assertThatThrownBy(() -> repository.claimReadyJobs(
                        BackgroundJobType.PROBLEM_SESSION_ENRICHMENT, " ", NOW, LEASE_EXPIRES_AT, 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> repository.claimReadyJobs(
                        BackgroundJobType.PROBLEM_SESSION_ENRICHMENT, "worker-a", NOW, NOW, 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> repository.claimReadyJobs(
                        BackgroundJobType.PROBLEM_SESSION_ENRICHMENT, "worker-a", NOW, LEASE_EXPIRES_AT, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void marksLeasedJobsSucceededAndClearsLeaseMetadata() {
        var userId = insertUser("background-success@example.com");
        var aggregateId = UUID.randomUUID();
        repository.insertIfAbsent(job(userId, aggregateId, NOW.minusSeconds(60)));
        var claimed = repository.claimReadyJobs(
                BackgroundJobType.PROBLEM_SESSION_ENRICHMENT, "worker-a", NOW, LEASE_EXPIRES_AT, 1);

        assertThat(repository.markSucceeded(claimed.get(0).id(), "worker-a", NOW.plusSeconds(10))).isTrue();

        assertThat(jobRow(aggregateId))
                .containsEntry("state", "SUCCEEDED")
                .containsEntry("lease_owner", null)
                .containsEntry("lease_expires_at", null)
                .containsEntry("safe_failure_code", null);
    }

    @Test
    void marksLeasedJobsRetryableAndSchedulesNextAttempt() {
        var userId = insertUser("background-retryable@example.com");
        var aggregateId = UUID.randomUUID();
        repository.insertIfAbsent(job(userId, aggregateId, NOW.minusSeconds(60)));
        var claimed = repository.claimReadyJobs(
                BackgroundJobType.PROBLEM_SESSION_ENRICHMENT, "worker-a", NOW, LEASE_EXPIRES_AT, 1);

        assertThat(repository.markRetryableFailure(
                        claimed.get(0).id(),
                        "worker-a",
                        "PROVIDER_TIMEOUT",
                        NOW.plusSeconds(30),
                        NOW.plusSeconds(1)))
                .isTrue();

        assertThat(jobRow(aggregateId))
                .containsEntry("state", "FAILED_RETRYABLE")
                .containsEntry("lease_owner", null)
                .containsEntry("lease_expires_at", null)
                .containsEntry("safe_failure_code", "PROVIDER_TIMEOUT");
        assertThat(((Timestamp) jobRow(aggregateId).get("next_attempt_at")).toInstant())
                .isEqualTo(NOW.plusSeconds(30));
        assertThat(repository.claimReadyJobs(
                        BackgroundJobType.PROBLEM_SESSION_ENRICHMENT,
                        "worker-b",
                        NOW.plusSeconds(29),
                        LEASE_EXPIRES_AT.plusSeconds(29),
                        1))
                .isEmpty();
        assertThat(repository.claimReadyJobs(
                        BackgroundJobType.PROBLEM_SESSION_ENRICHMENT,
                        "worker-b",
                        NOW.plusSeconds(30),
                        LEASE_EXPIRES_AT.plusSeconds(30),
                        1))
                .singleElement()
                .satisfies(job -> {
                    assertThat(job.aggregateId()).isEqualTo(aggregateId);
                    assertThat(job.state()).isEqualTo(BackgroundJobState.LEASED);
                    assertThat(job.leaseOwner()).isEqualTo("worker-b");
                    assertThat(job.attempts()).isEqualTo(2);
                });
    }

    @Test
    void marksLeasedJobsTerminalFailureAndProtectsAgainstWrongLeaseOwner() {
        var userId = insertUser("background-terminal@example.com");
        var aggregateId = UUID.randomUUID();
        repository.insertIfAbsent(job(userId, aggregateId, NOW.minusSeconds(60)));
        var claimed = repository.claimReadyJobs(
                BackgroundJobType.PROBLEM_SESSION_ENRICHMENT, "worker-a", NOW, LEASE_EXPIRES_AT, 1);

        assertThat(repository.markTerminalFailure(claimed.get(0).id(), "worker-b", "INVALID_INPUT", NOW.plusSeconds(1)))
                .isFalse();
        assertThat(repository.markTerminalFailure(claimed.get(0).id(), "worker-a", "INVALID_INPUT", NOW.plusSeconds(1)))
                .isTrue();

        assertThat(jobRow(aggregateId))
                .containsEntry("state", "FAILED_TERMINAL")
                .containsEntry("lease_owner", null)
                .containsEntry("lease_expires_at", null)
                .containsEntry("safe_failure_code", "INVALID_INPUT");
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

    private BackgroundJob job(UUID userId, UUID aggregateId, Instant nextAttemptAt) {
        return new BackgroundJob(
                UUID.randomUUID(),
                userId,
                BackgroundJobAggregateType.PROBLEM_SESSION,
                aggregateId,
                BackgroundJobType.PROBLEM_SESSION_ENRICHMENT,
                BackgroundJobState.READY,
                0,
                nextAttemptAt,
                null,
                null,
                "{\"problemSessionId\":\"" + aggregateId + "\"}",
                nextAttemptAt,
                nextAttemptAt);
    }

    private int countJobs() {
        return jdbcTemplate.queryForObject("select count(*) from background_jobs", Integer.class);
    }

    private String jobState(UUID aggregateId) {
        return jdbcTemplate.queryForObject(
                "select state from background_jobs where aggregate_id = ?", String.class, aggregateId);
    }

    private String leaseOwner(UUID aggregateId) {
        return jdbcTemplate.queryForObject(
                "select lease_owner from background_jobs where aggregate_id = ?", String.class, aggregateId);
    }

    private java.util.Map<String, Object> jobRow(UUID aggregateId) {
        return jdbcTemplate.queryForMap(
                """
                select state, lease_owner, lease_expires_at, safe_failure_code, next_attempt_at
                from background_jobs
                where aggregate_id = ?
                """,
                aggregateId);
    }

    private void expireLease(UUID aggregateId, Instant leaseExpiresAt) {
        jdbcTemplate.update(
                "update background_jobs set lease_expires_at = ? where aggregate_id = ?",
                Timestamp.from(leaseExpiresAt),
                aggregateId);
    }

    private Connection lockJobByAggregateId(UUID aggregateId) throws SQLException {
        var connection = DriverManager.getConnection(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        connection.setAutoCommit(false);
        try (var statement =
                connection.prepareStatement("select id from background_jobs where aggregate_id = ? for update")) {
            statement.setObject(1, aggregateId);
            statement.executeQuery();
        }
        return connection;
    }
}
