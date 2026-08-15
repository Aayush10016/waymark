package io.github.aayush10016.trail.problem.domain.model;

import java.time.Instant;
import java.util.Objects;

/**
 * Exact final solution text with its authorship evidence.
 *
 * @param text exact user-provided solution text
 * @param author source that authored the solution text
 * @param authoredAt time the solution was authored
 */
public record FinalSolution(String text, FinalSolutionAuthor author, Instant authoredAt) {

    /**
     * Creates a validated final solution value while preserving exact text.
     */
    public FinalSolution {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("final solution text must not be blank");
        }
        Objects.requireNonNull(author, "author");
        Objects.requireNonNull(authoredAt, "authoredAt");
    }

    /**
     * Creates a user-authored final solution.
     *
     * @param text exact user-provided solution text
     * @param authoredAt time the user authored the solution
     * @return validated final solution
     */
    public static FinalSolution userAuthored(String text, Instant authoredAt) {
        return new FinalSolution(text, FinalSolutionAuthor.USER, authoredAt);
    }

    /**
     * Returns whether this final solution can authoritatively solve a Problem Session.
     */
    public boolean isUserAuthored() {
        return author == FinalSolutionAuthor.USER;
    }
}
