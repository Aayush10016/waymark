package io.github.aayush10016.trail.backgroundjob.domain.port;

import io.github.aayush10016.trail.backgroundjob.domain.model.BackgroundJob;
import io.github.aayush10016.trail.backgroundjob.domain.model.BackgroundJobType;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Persistence boundary for durable background job recording.
 */
public interface BackgroundJobRepository {

    /**
     * Inserts the job unless an equivalent aggregate job was already recorded.
     *
     * @return true when a new job was inserted
     */
    boolean insertIfAbsent(BackgroundJob job);

    /**
     * Atomically claims due ready jobs or expired leased jobs for a bounded worker lease.
     */
    List<BackgroundJob> claimReadyJobs(
            BackgroundJobType jobType, String leaseOwner, Instant now, Instant leaseExpiresAt, int limit);

    /**
     * Marks a leased job as succeeded when the caller still owns the lease.
     */
    boolean markSucceeded(UUID jobId, String leaseOwner, Instant completedAt);

    /**
     * Releases a leased job back to ready state with a bounded retry time.
     */
    boolean markRetryableFailure(
            UUID jobId, String leaseOwner, String safeFailureCode, Instant nextAttemptAt, Instant now);

    /**
     * Marks a leased job as permanently failed when the caller still owns the lease.
     */
    boolean markTerminalFailure(UUID jobId, String leaseOwner, String safeFailureCode, Instant completedAt);
}
