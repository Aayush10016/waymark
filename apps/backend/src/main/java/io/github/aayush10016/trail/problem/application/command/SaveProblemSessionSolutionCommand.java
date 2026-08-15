package io.github.aayush10016.trail.problem.application.command;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Command to save the exact user-authored final solution for a Problem Session.
 *
 * @param problemSessionId target Problem Session ID
 * @param expectedVersion optimistic version from If-Match
 * @param idempotencyKey client retry key for duplicate submission safety
 * @param finalSolutionText exact user-authored final solution text
 * @param authoredAt server-side authorship timestamp
 */
public record SaveProblemSessionSolutionCommand(
        UUID problemSessionId,
        long expectedVersion,
        String idempotencyKey,
        String finalSolutionText,
        Instant authoredAt) {

    /**
     * Creates a validated command.
     */
    public SaveProblemSessionSolutionCommand {
        Objects.requireNonNull(problemSessionId, "problemSessionId");
        Objects.requireNonNull(idempotencyKey, "idempotencyKey");
        Objects.requireNonNull(finalSolutionText, "finalSolutionText");
        Objects.requireNonNull(authoredAt, "authoredAt");
        if (expectedVersion < 0) {
            throw new IllegalArgumentException("expectedVersion must be non-negative");
        }
    }
}
