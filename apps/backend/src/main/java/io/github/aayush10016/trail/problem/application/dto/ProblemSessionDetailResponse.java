package io.github.aayush10016.trail.problem.application.dto;

import io.github.aayush10016.trail.problem.domain.model.ProblemSessionSnapshot;
import io.github.aayush10016.trail.problem.domain.model.SessionArtifact;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Bounded user-owned Problem Session detail with ordered durable artifacts.
 *
 * @param id Problem Session ID
 * @param status persisted lifecycle status
 * @param source session source
 * @param version optimistic version
 * @param title optional bounded title
 * @param description optional bounded description
 * @param localNote optional user local note
 * @param startedAt session start time
 * @param endedAt optional capture-boundary end time
 * @param updatedAt last update time
 * @param artifacts ordered bounded active artifact response
 * @param omittedArtifactCount count of active artifacts omitted by response bound
 */
public record ProblemSessionDetailResponse(
        UUID id,
        String status,
        String source,
        long version,
        String title,
        String description,
        String localNote,
        Instant startedAt,
        Instant endedAt,
        Instant updatedAt,
        List<SessionArtifactDetailResponse> artifacts,
        int omittedArtifactCount) {

    /**
     * Creates an immutable detail response.
     */
    public ProblemSessionDetailResponse {
        artifacts = List.copyOf(artifacts);
        if (omittedArtifactCount < 0) {
            throw new IllegalArgumentException("omittedArtifactCount must be non-negative");
        }
    }

    /**
     * Builds a response from a domain snapshot and bounded artifact list.
     */
    public static ProblemSessionDetailResponse from(
            ProblemSessionSnapshot snapshot, List<SessionArtifact> artifacts, int omittedArtifactCount) {
        return new ProblemSessionDetailResponse(
                snapshot.id(),
                snapshot.session().status().name(),
                snapshot.source().name(),
                snapshot.version(),
                snapshot.title(),
                snapshot.description(),
                snapshot.localNote(),
                snapshot.startedAt(),
                snapshot.endedAt(),
                snapshot.updatedAt(),
                artifacts.stream().map(SessionArtifactDetailResponse::from).toList(),
                omittedArtifactCount);
    }
}
