package io.github.aayush10016.trail.problem.domain.model;

/**
 * Predictable domain failure for illegal Problem Session lifecycle transitions.
 */
public final class ProblemSessionTransitionException extends IllegalStateException {

    private static final long serialVersionUID = 1L;
    private static final String CODE = "ILLEGAL_PROBLEM_SESSION_TRANSITION";

    private final ProblemSessionStatus currentStatus;
    private final ProblemSessionStatus attemptedStatus;

    private ProblemSessionTransitionException(ProblemSessionStatus currentStatus, ProblemSessionStatus attemptedStatus) {
        super("Illegal Problem Session transition from " + currentStatus + " to " + attemptedStatus);
        this.currentStatus = currentStatus;
        this.attemptedStatus = attemptedStatus;
    }

    /**
     * Creates a transition exception without sensitive Problem Session content.
     */
    public static ProblemSessionTransitionException illegal(
            ProblemSessionStatus currentStatus, ProblemSessionStatus attemptedStatus) {
        return new ProblemSessionTransitionException(currentStatus, attemptedStatus);
    }

    /**
     * Stable safe error code.
     */
    public String code() {
        return CODE;
    }

    /**
     * State before the rejected transition.
     */
    public ProblemSessionStatus currentStatus() {
        return currentStatus;
    }

    /**
     * State that the caller attempted to enter.
     */
    public ProblemSessionStatus attemptedStatus() {
        return attemptedStatus;
    }
}
