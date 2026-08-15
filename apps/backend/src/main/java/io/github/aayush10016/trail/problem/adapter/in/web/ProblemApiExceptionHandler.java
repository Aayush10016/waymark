package io.github.aayush10016.trail.problem.adapter.in.web;

import io.github.aayush10016.trail.problem.application.service.InvalidProblemSessionCommandException;
import io.github.aayush10016.trail.problem.application.service.ProblemSessionNotFoundException;
import io.github.aayush10016.trail.problem.application.service.ProblemSessionVersionConflictException;
import io.github.aayush10016.trail.problem.domain.model.ProblemSessionTransitionException;
import java.util.UUID;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Maps Problem Session API failures to RFC 9457 problem details without sensitive content.
 */
@RestControllerAdvice(assignableTypes = {ProblemSessionCommandController.class, ProblemSessionDetailController.class})
public class ProblemApiExceptionHandler {

    /**
     * Handles safe Problem Session API exceptions.
     */
    @ExceptionHandler(ProblemApiException.class)
    public ResponseEntity<ProblemDetail> handleProblemApiException(ProblemApiException exception) {
        return problem(exception.status(), exception.code(), exception.getMessage());
    }

    /**
     * Handles user-scoped missing sessions.
     */
    @ExceptionHandler(ProblemSessionNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleNotFound() {
        return problem(HttpStatus.NOT_FOUND, "PROBLEM_SESSION_NOT_FOUND", "Problem Session was not found.");
    }

    /**
     * Handles stale optimistic versions.
     */
    @ExceptionHandler(ProblemSessionVersionConflictException.class)
    public ResponseEntity<ProblemDetail> handleVersionConflict() {
        return problem(HttpStatus.PRECONDITION_FAILED, "PROBLEM_SESSION_VERSION_CONFLICT", "Problem Session version is stale.");
    }

    /**
     * Handles invalid state transitions.
     */
    @ExceptionHandler(ProblemSessionTransitionException.class)
    public ResponseEntity<ProblemDetail> handleIllegalTransition() {
        return problem(HttpStatus.CONFLICT, "PROBLEM_SESSION_ILLEGAL_TRANSITION", "Problem Session state transition is invalid.");
    }

    /**
     * Handles semantically invalid commands.
     */
    @ExceptionHandler(InvalidProblemSessionCommandException.class)
    public ResponseEntity<ProblemDetail> handleInvalidCommand(InvalidProblemSessionCommandException exception) {
        return problem(HttpStatus.BAD_REQUEST, "PROBLEM_SESSION_INVALID_REQUEST", exception.getMessage());
    }

    /**
     * Handles malformed request bodies.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ProblemDetail> handleNotReadable() {
        return problem(HttpStatus.BAD_REQUEST, "PROBLEM_SESSION_INVALID_REQUEST", "Problem Session request is invalid.");
    }

    /**
     * Handles persistence failures with a generic safe error.
     */
    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ProblemDetail> handleDataAccessException() {
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "PROBLEM_SESSION_COMMAND_FAILED", "Problem Session command failed.");
    }

    private ResponseEntity<ProblemDetail> problem(HttpStatus status, String code, String detail) {
        var problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(status.getReasonPhrase());
        problem.setProperty("code", code);
        problem.setProperty("requestId", UUID.randomUUID().toString());
        return ResponseEntity.status(status).body(problem);
    }
}
