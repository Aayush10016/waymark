package io.github.aayush10016.trail.problem.domain.model;

/**
 * Lifecycle states for a Problem Session.
 */
public enum ProblemSessionStatus {
    /**
     * Candidate problem detected or manually started but not ready for solution review.
     */
    CANDIDATE,

    /**
     * Candidate is ready for user review and can be solved, dismissed, or marked unsolved.
     */
    REVIEWABLE,

    /**
     * User authored an exact final solution.
     */
    SOLVED,

    /**
     * User dismissed the candidate as not a relevant problem.
     */
    DISMISSED,

    /**
     * User indicated the problem was not solved.
     */
    UNSOLVED,

    /**
     * Session was deleted and must be excluded from active reads.
     */
    DELETED
}
