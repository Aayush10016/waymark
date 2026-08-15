package io.github.aayush10016.trail.backgroundjob.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Durable background job record created by synchronous application commands.
 *
 * @param id stable job identifier
 * @param userId owning user identifier
 * @param aggregateType aggregate category the job belongs to
 * @param aggregateId aggregate identifier
 * @param jobType job work type
 * @param state durable lifecycle state
 * @param attempts completed attempt count
 * @param nextAttemptAt earliest time the job may be processed
 * @param leaseOwner worker lease owner, present only while leased
 * @param leaseExpiresAt worker lease expiry, present only while leased
 * @param payloadJson ID-only JSON payload
 * @param createdAt creation time
 * @param updatedAt last update time
 */
public record BackgroundJob(
        UUID id,
        UUID userId,
        BackgroundJobAggregateType aggregateType,
        UUID aggregateId,
        BackgroundJobType jobType,
        BackgroundJobState state,
        int attempts,
        Instant nextAttemptAt,
        String leaseOwner,
        Instant leaseExpiresAt,
        String payloadJson,
        Instant createdAt,
        Instant updatedAt) {

    /**
     * Creates a validated durable job record.
     */
    public BackgroundJob {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(aggregateType, "aggregateType");
        Objects.requireNonNull(aggregateId, "aggregateId");
        Objects.requireNonNull(jobType, "jobType");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(nextAttemptAt, "nextAttemptAt");
        Objects.requireNonNull(payloadJson, "payloadJson");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        if (attempts < 0) {
            throw new IllegalArgumentException("attempts must be non-negative");
        }
        if (state == BackgroundJobState.LEASED) {
            if (leaseOwner == null || leaseOwner.isBlank()) {
                throw new IllegalArgumentException("leased jobs require a lease owner");
            }
            Objects.requireNonNull(leaseExpiresAt, "leaseExpiresAt");
        } else if (leaseOwner != null || leaseExpiresAt != null) {
            throw new IllegalArgumentException("only leased jobs may carry lease metadata");
        }
        if (updatedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("updatedAt must not be before createdAt");
        }
    }

    /**
     * Creates the post-solution enrichment job required after a Problem Session is solved.
     */
    public static BackgroundJob problemSessionEnrichment(UUID id, UUID userId, UUID problemSessionId, Instant now) {
        return new BackgroundJob(
                id,
                userId,
                BackgroundJobAggregateType.PROBLEM_SESSION,
                problemSessionId,
                BackgroundJobType.PROBLEM_SESSION_ENRICHMENT,
                BackgroundJobState.READY,
                0,
                now,
                null,
                null,
                "{\"problemSessionId\":\"" + problemSessionId + "\"}",
                now,
                now);
    }

    /**
     * Creates the post-solution solved-session embedding job.
     */
    public static BackgroundJob solvedProblemSessionEmbedding(UUID id, UUID userId, UUID problemSessionId, Instant now) {
        return new BackgroundJob(
                id,
                userId,
                BackgroundJobAggregateType.PROBLEM_SESSION,
                problemSessionId,
                BackgroundJobType.SOLVED_PROBLEM_SESSION_EMBEDDING,
                BackgroundJobState.READY,
                0,
                now,
                null,
                null,
                "{\"problemSessionId\":\"" + problemSessionId + "\"}",
                now,
                now);
    }
}
