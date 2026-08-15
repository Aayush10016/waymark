package io.github.aayush10016.trail.problem.application.dto;

import io.github.aayush10016.trail.problem.domain.model.FinalSolution;
import io.github.aayush10016.trail.problem.domain.model.ProblemSessionSnapshot;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Stable response for saving a Problem Session final solution.
 *
 * @param id Problem Session ID
 * @param status lifecycle status
 * @param source creation source
 * @param version optimistic version
 * @param title optional title
 * @param finalSolution exact user-authored final solution text
 * @param finalSolutionAuthor final solution authorship source
 * @param solutionAuthoredAt server-side authorship timestamp
 * @param startedAt session start time
 * @param endedAt optional capture-boundary end time
 * @param updatedAt last update time
 */
public record ProblemSessionSolutionResponse(
        UUID id,
        String status,
        String source,
        long version,
        String title,
        String finalSolution,
        String finalSolutionAuthor,
        Instant solutionAuthoredAt,
        Instant startedAt,
        Instant endedAt,
        Instant updatedAt) {

    /**
     * Creates a response from a solved snapshot.
     */
    public static ProblemSessionSolutionResponse from(ProblemSessionSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        FinalSolution solution = snapshot.session().finalSolution().orElseThrow();
        return new ProblemSessionSolutionResponse(
                snapshot.id(),
                snapshot.session().status().name(),
                snapshot.source().name(),
                snapshot.version(),
                snapshot.title(),
                solution.text(),
                solution.author().name(),
                solution.authoredAt(),
                snapshot.startedAt(),
                snapshot.endedAt(),
                snapshot.updatedAt());
    }
}
