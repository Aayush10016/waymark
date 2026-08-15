package io.github.aayush10016.trail.problem.domain.model;

import java.util.EnumSet;
import java.util.Optional;

/**
 * Framework-free Problem Session domain state machine.
 */
public final class ProblemSession {

    private static final EnumSet<ProblemSessionStatus> CANDIDATE_OR_REVIEWABLE =
            EnumSet.of(ProblemSessionStatus.CANDIDATE, ProblemSessionStatus.REVIEWABLE);

    private final ProblemSessionStatus status;
    private final FinalSolution finalSolution;

    private ProblemSession(ProblemSessionStatus status, FinalSolution finalSolution) {
        this.status = status;
        this.finalSolution = finalSolution;
        validateInvariants(status, finalSolution);
    }

    /**
     * Creates a new candidate Problem Session.
     */
    public static ProblemSession candidate() {
        return new ProblemSession(ProblemSessionStatus.CANDIDATE, null);
    }

    /**
     * Reconstitutes a persisted Problem Session while enforcing domain invariants.
     */
    public static ProblemSession rehydrate(ProblemSessionStatus status, FinalSolution finalSolution) {
        return new ProblemSession(status, finalSolution);
    }

    /**
     * Current lifecycle state.
     */
    public ProblemSessionStatus status() {
        return status;
    }

    /**
     * Authoritative final solution when this session is solved.
     */
    public Optional<FinalSolution> finalSolution() {
        return Optional.ofNullable(finalSolution);
    }

    /**
     * Moves a candidate session into review.
     */
    public ProblemSession markReviewable() {
        requireTransitionTo(ProblemSessionStatus.REVIEWABLE, ProblemSessionStatus.CANDIDATE);
        return new ProblemSession(ProblemSessionStatus.REVIEWABLE, null);
    }

    /**
     * Solves a reviewable session with exact user-authored final solution text.
     */
    public ProblemSession solve(FinalSolution solution) {
        requireTransitionTo(ProblemSessionStatus.SOLVED, ProblemSessionStatus.REVIEWABLE);
        if (solution == null || !solution.isUserAuthored()) {
            throw ProblemSessionTransitionException.illegal(status, ProblemSessionStatus.SOLVED);
        }
        return new ProblemSession(ProblemSessionStatus.SOLVED, solution);
    }

    /**
     * Replaces the exact user-authored final solution while keeping the session solved.
     */
    public ProblemSession updateSolution(FinalSolution solution) {
        requireTransitionTo(ProblemSessionStatus.SOLVED, ProblemSessionStatus.SOLVED);
        if (solution == null || !solution.isUserAuthored()) {
            throw ProblemSessionTransitionException.illegal(status, ProblemSessionStatus.SOLVED);
        }
        return new ProblemSession(ProblemSessionStatus.SOLVED, solution);
    }

    /**
     * Dismisses a candidate or reviewable session as not a problem.
     */
    public ProblemSession dismiss() {
        requireTransitionTo(ProblemSessionStatus.DISMISSED, CANDIDATE_OR_REVIEWABLE);
        return new ProblemSession(ProblemSessionStatus.DISMISSED, null);
    }

    /**
     * Marks a candidate or reviewable session as unfinished or not yet solved.
     */
    public ProblemSession markUnsolved() {
        requireTransitionTo(ProblemSessionStatus.UNSOLVED, CANDIDATE_OR_REVIEWABLE);
        return new ProblemSession(ProblemSessionStatus.UNSOLVED, null);
    }

    /**
     * Marks a solved session deleted.
     */
    public ProblemSession delete() {
        requireTransitionTo(ProblemSessionStatus.DELETED, ProblemSessionStatus.SOLVED);
        return new ProblemSession(ProblemSessionStatus.DELETED, null);
    }

    /**
     * Returns whether no further lifecycle transition is allowed.
     */
    public boolean isTerminal() {
        return status == ProblemSessionStatus.DISMISSED
                || status == ProblemSessionStatus.UNSOLVED
                || status == ProblemSessionStatus.DELETED;
    }

    private void requireTransitionTo(ProblemSessionStatus nextStatus, ProblemSessionStatus allowedCurrentStatus) {
        if (status != allowedCurrentStatus) {
            throw ProblemSessionTransitionException.illegal(status, nextStatus);
        }
    }

    private void requireTransitionTo(ProblemSessionStatus nextStatus, EnumSet<ProblemSessionStatus> allowedCurrentStatuses) {
        if (!allowedCurrentStatuses.contains(status)) {
            throw ProblemSessionTransitionException.illegal(status, nextStatus);
        }
    }

    private static void validateInvariants(ProblemSessionStatus status, FinalSolution finalSolution) {
        if (status == ProblemSessionStatus.SOLVED && (finalSolution == null || !finalSolution.isUserAuthored())) {
            throw new IllegalArgumentException("solved Problem Session requires a user-authored final solution");
        }
        if (status != ProblemSessionStatus.SOLVED && finalSolution != null) {
            throw new IllegalArgumentException("only solved Problem Sessions can carry a final solution");
        }
    }
}
