package io.github.aayush10016.trail.problem.adapter.in.web;

import org.springframework.http.HttpStatus;

/**
 * Safe Problem Session API exception with stable error code.
 */
public class ProblemApiException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final HttpStatus status;
    private final String code;

    private ProblemApiException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    /**
     * Creates an authentication failure.
     */
    public static ProblemApiException unauthorized() {
        return new ProblemApiException(
                HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED", "Authentication is required.");
    }

    /**
     * Creates a malformed or invalid request failure.
     */
    public static ProblemApiException badRequest(String code, String message) {
        return new ProblemApiException(HttpStatus.BAD_REQUEST, code, message);
    }

    /**
     * Creates a missing resource failure.
     */
    public static ProblemApiException notFound() {
        return new ProblemApiException(HttpStatus.NOT_FOUND, "PROBLEM_SESSION_NOT_FOUND", "Problem Session was not found.");
    }

    /**
     * Creates a missing precondition failure.
     */
    public static ProblemApiException preconditionRequired() {
        return new ProblemApiException(
                HttpStatus.PRECONDITION_REQUIRED, "PROBLEM_SESSION_VERSION_REQUIRED", "If-Match is required.");
    }

    /**
     * Creates a stale version failure.
     */
    public static ProblemApiException versionConflict() {
        return new ProblemApiException(
                HttpStatus.PRECONDITION_FAILED, "PROBLEM_SESSION_VERSION_CONFLICT", "Problem Session version is stale.");
    }

    /**
     * Creates an illegal state transition failure.
     */
    public static ProblemApiException illegalTransition() {
        return new ProblemApiException(
                HttpStatus.CONFLICT, "PROBLEM_SESSION_ILLEGAL_TRANSITION", "Problem Session state transition is invalid.");
    }

    HttpStatus status() {
        return status;
    }

    String code() {
        return code;
    }
}
