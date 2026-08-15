package io.github.aayush10016.trail.backgroundjob.adapter.out.persistence;

import io.github.aayush10016.trail.backgroundjob.domain.model.BackgroundJob;
import io.github.aayush10016.trail.backgroundjob.domain.model.BackgroundJobAggregateType;
import io.github.aayush10016.trail.backgroundjob.domain.model.BackgroundJobState;
import io.github.aayush10016.trail.backgroundjob.domain.model.BackgroundJobType;
import io.github.aayush10016.trail.backgroundjob.domain.port.BackgroundJobRepository;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/**
 * PostgreSQL adapter for durable background job recording.
 */
@Repository
public class JdbcBackgroundJobRepository implements BackgroundJobRepository {

    private static final RowMapper<BackgroundJob> BACKGROUND_JOB_ROW_MAPPER = new BackgroundJobRowMapper();

    private final JdbcTemplate jdbcTemplate;

    /**
     * Creates the repository.
     */
    public JdbcBackgroundJobRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate");
    }

    @Override
    public boolean insertIfAbsent(BackgroundJob job) {
        Objects.requireNonNull(job, "job");

        var inserted = jdbcTemplate.update(
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
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?)
                on conflict (user_id, job_type, aggregate_type, aggregate_id) do nothing
                """,
                job.id(),
                job.userId(),
                job.aggregateType().name(),
                job.aggregateId(),
                job.jobType().name(),
                job.state().name(),
                job.attempts(),
                Timestamp.from(job.nextAttemptAt()),
                job.payloadJson(),
                Timestamp.from(job.createdAt()),
                Timestamp.from(job.updatedAt()));
        return inserted == 1;
    }

    @Override
    public List<BackgroundJob> claimReadyJobs(
            BackgroundJobType jobType, String leaseOwner, Instant now, Instant leaseExpiresAt, int limit) {
        Objects.requireNonNull(jobType, "jobType");
        Objects.requireNonNull(now, "now");
        Objects.requireNonNull(leaseExpiresAt, "leaseExpiresAt");
        if (leaseOwner == null || leaseOwner.isBlank()) {
            throw new IllegalArgumentException("leaseOwner must not be blank");
        }
        if (!leaseExpiresAt.isAfter(now)) {
            throw new IllegalArgumentException("leaseExpiresAt must be after now");
        }
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be positive");
        }

        return jdbcTemplate.query(
                """
                with claimable_jobs as (
                    select id
                    from background_jobs
                    where job_type = ?
                        and (
                            (
                                state in ('READY', 'FAILED_RETRYABLE')
                                and next_attempt_at <= ?
                            )
                            or (
                                state = 'LEASED'
                                and lease_expires_at <= ?
                            )
                        )
                    order by next_attempt_at, created_at, id
                    limit ?
                    for update skip locked
                )
                update background_jobs
                set state = 'LEASED',
                    lease_owner = ?,
                    lease_expires_at = ?,
                    attempts = attempts + 1,
                    updated_at = ?
                from claimable_jobs
                where background_jobs.id = claimable_jobs.id
                returning
                    background_jobs.id,
                    background_jobs.user_id,
                    background_jobs.aggregate_type,
                    background_jobs.aggregate_id,
                    background_jobs.job_type,
                    background_jobs.state,
                    background_jobs.attempts,
                    background_jobs.next_attempt_at,
                    background_jobs.lease_owner,
                    background_jobs.lease_expires_at,
                    background_jobs.payload::text as payload_json,
                    background_jobs.created_at,
                    background_jobs.updated_at
                """,
                BACKGROUND_JOB_ROW_MAPPER,
                jobType.name(),
                Timestamp.from(now),
                Timestamp.from(now),
                limit,
                leaseOwner.trim(),
                Timestamp.from(leaseExpiresAt),
                Timestamp.from(now));
    }

    @Override
    public boolean markSucceeded(UUID jobId, String leaseOwner, Instant completedAt) {
        return markCompleted(jobId, leaseOwner, BackgroundJobState.SUCCEEDED, null, completedAt);
    }

    @Override
    public boolean markRetryableFailure(
            UUID jobId, String leaseOwner, String safeFailureCode, Instant nextAttemptAt, Instant now) {
        Objects.requireNonNull(jobId, "jobId");
        Objects.requireNonNull(nextAttemptAt, "nextAttemptAt");
        Objects.requireNonNull(now, "now");
        var sanitizedLeaseOwner = requireLeaseOwner(leaseOwner);
        var sanitizedFailureCode = requireSafeFailureCode(safeFailureCode);
        if (nextAttemptAt.isBefore(now)) {
            throw new IllegalArgumentException("nextAttemptAt must not be before now");
        }

        var updated = jdbcTemplate.update(
                """
                update background_jobs
                set state = 'FAILED_RETRYABLE',
                    lease_owner = null,
                    lease_expires_at = null,
                    next_attempt_at = ?,
                    safe_failure_code = ?,
                    updated_at = ?
                where id = ?
                    and state = 'LEASED'
                    and lease_owner = ?
                """,
                Timestamp.from(nextAttemptAt),
                sanitizedFailureCode,
                Timestamp.from(now),
                jobId,
                sanitizedLeaseOwner);
        return updated == 1;
    }

    @Override
    public boolean markTerminalFailure(UUID jobId, String leaseOwner, String safeFailureCode, Instant completedAt) {
        return markCompleted(
                jobId,
                leaseOwner,
                BackgroundJobState.FAILED_TERMINAL,
                requireSafeFailureCode(safeFailureCode),
                completedAt);
    }

    private boolean markCompleted(
            UUID jobId,
            String leaseOwner,
            BackgroundJobState terminalState,
            String safeFailureCode,
            Instant completedAt) {
        Objects.requireNonNull(jobId, "jobId");
        Objects.requireNonNull(terminalState, "terminalState");
        Objects.requireNonNull(completedAt, "completedAt");
        if (terminalState != BackgroundJobState.SUCCEEDED && terminalState != BackgroundJobState.FAILED_TERMINAL) {
            throw new IllegalArgumentException("terminalState must be terminal");
        }

        var updated = jdbcTemplate.update(
                """
                update background_jobs
                set state = ?,
                    lease_owner = null,
                    lease_expires_at = null,
                    safe_failure_code = ?,
                    completed_at = ?,
                    updated_at = ?
                where id = ?
                    and state = 'LEASED'
                    and lease_owner = ?
                """,
                terminalState.name(),
                safeFailureCode,
                Timestamp.from(completedAt),
                Timestamp.from(completedAt),
                jobId,
                requireLeaseOwner(leaseOwner));
        return updated == 1;
    }

    private static String requireLeaseOwner(String leaseOwner) {
        if (leaseOwner == null || leaseOwner.isBlank()) {
            throw new IllegalArgumentException("leaseOwner must not be blank");
        }
        var sanitized = leaseOwner.strip();
        if (sanitized.length() > 128) {
            throw new IllegalArgumentException("leaseOwner must be bounded");
        }
        return sanitized;
    }

    private static String requireSafeFailureCode(String safeFailureCode) {
        if (safeFailureCode == null || safeFailureCode.isBlank()) {
            throw new IllegalArgumentException("safeFailureCode must not be blank");
        }
        var sanitized = safeFailureCode.strip();
        if (sanitized.length() > 128) {
            throw new IllegalArgumentException("safeFailureCode must be bounded");
        }
        return sanitized;
    }

    private static final class BackgroundJobRowMapper implements RowMapper<BackgroundJob> {

        @Override
        public BackgroundJob mapRow(ResultSet resultSet, int rowNumber) throws SQLException {
            var leaseExpiresAt = resultSet.getTimestamp("lease_expires_at");
            return new BackgroundJob(
                    resultSet.getObject("id", java.util.UUID.class),
                    resultSet.getObject("user_id", java.util.UUID.class),
                    BackgroundJobAggregateType.valueOf(resultSet.getString("aggregate_type")),
                    resultSet.getObject("aggregate_id", java.util.UUID.class),
                    BackgroundJobType.valueOf(resultSet.getString("job_type")),
                    BackgroundJobState.valueOf(resultSet.getString("state")),
                    resultSet.getInt("attempts"),
                    toInstant(resultSet.getTimestamp("next_attempt_at")),
                    resultSet.getString("lease_owner"),
                    leaseExpiresAt == null ? null : leaseExpiresAt.toInstant(),
                    resultSet.getString("payload_json"),
                    toInstant(resultSet.getTimestamp("created_at")),
                    toInstant(resultSet.getTimestamp("updated_at")));
        }

        private static Instant toInstant(Timestamp timestamp) {
            return timestamp.toInstant();
        }
    }
}
