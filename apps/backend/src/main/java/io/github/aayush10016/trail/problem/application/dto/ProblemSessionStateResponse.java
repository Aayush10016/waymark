package io.github.aayush10016.trail.problem.application.dto;

import io.github.aayush10016.trail.problem.domain.model.ProblemSessionSnapshot;
import io.github.aayush10016.trail.problem.domain.model.ProblemSessionSource;
import io.github.aayush10016.trail.problem.domain.model.ProblemSessionStatus;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Stable application response for Problem Session state-changing commands.
 *
 * @param id Problem Session ID
 * @param status lifecycle status
 * @param source creation source
 * @param version optimistic version
 * @param title optional title
 * @param startedAt session start time
 * @param endedAt optional capture-boundary end time
 * @param updatedAt last update time
 */
public record ProblemSessionStateResponse(
        UUID id,
        ProblemSessionStatus status,
        ProblemSessionSource source,
        long version,
        String title,
        Instant startedAt,
        Instant endedAt,
        Instant updatedAt) {

    /**
     * Creates a response from a persisted snapshot.
     */
    public static ProblemSessionStateResponse from(ProblemSessionSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        return new ProblemSessionStateResponse(
                snapshot.id(),
                snapshot.session().status(),
                snapshot.source(),
                snapshot.version(),
                snapshot.title(),
                snapshot.startedAt(),
                snapshot.endedAt(),
                snapshot.updatedAt());
    }
}
