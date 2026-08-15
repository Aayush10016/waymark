package io.github.aayush10016.trail.problem.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Persistable Problem Session snapshot owned by one user.
 *
 * @param id stable Problem Session identifier
 * @param source how the session was created
 * @param session lifecycle state and final-solution invariant
 * @param version optimistic concurrency version
 * @param title optional user-confirmed or derived title
 * @param description optional problem description
 * @param localNote optional bounded note
 * @param startedAt session start time
 * @param endedAt optional session end time
 * @param createdAt persistence creation time
 * @param updatedAt persistence update time
 * @param deletedAt optional deletion timestamp
 */
public record ProblemSessionSnapshot(
        UUID id,
        ProblemSessionSource source,
        ProblemSession session,
        long version,
        String title,
        String description,
        String localNote,
        Instant startedAt,
        Instant endedAt,
        Instant createdAt,
        Instant updatedAt,
        Instant deletedAt) {

    /**
     * Creates a validated immutable snapshot.
     */
    public ProblemSessionSnapshot {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(session, "session");
        Objects.requireNonNull(startedAt, "startedAt");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        if (version < 0) {
            throw new IllegalArgumentException("version must be non-negative");
        }
        if (endedAt != null && endedAt.isBefore(startedAt)) {
            throw new IllegalArgumentException("endedAt must not be before startedAt");
        }
        if (updatedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("updatedAt must not be before createdAt");
        }
    }
}
