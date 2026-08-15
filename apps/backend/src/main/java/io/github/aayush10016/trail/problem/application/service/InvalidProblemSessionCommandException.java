package io.github.aayush10016.trail.problem.application.service;

/**
 * Raised when a Problem Session command is semantically invalid.
 */
public class InvalidProblemSessionCommandException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * Creates the exception.
     */
    public InvalidProblemSessionCommandException(String message) {
        super(message);
    }
}
