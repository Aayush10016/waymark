package io.github.aayush10016.trail.problem.application.command;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Command to close a Problem Session capture boundary.
 *
 * @param problemSessionId target Problem Session ID
 * @param expectedVersion optimistic version from If-Match
 * @param finishedAt explicit finish timestamp
 */
public record FinishProblemSessionCommand(UUID problemSessionId, long expectedVersion, Instant finishedAt) {

    /**
     * Creates a validated command.
     */
    public FinishProblemSessionCommand {
        Objects.requireNonNull(problemSessionId, "problemSessionId");
        Objects.requireNonNull(finishedAt, "finishedAt");
        if (expectedVersion < 0) {
            throw new IllegalArgumentException("expectedVersion must be non-negative");
        }
    }
}
