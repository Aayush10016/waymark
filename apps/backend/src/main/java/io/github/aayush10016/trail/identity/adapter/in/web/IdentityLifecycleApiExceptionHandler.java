package io.github.aayush10016.trail.identity.adapter.in.web;

import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Maps identity lifecycle API failures to safe problem details.
 */
@RestControllerAdvice(assignableTypes = {IdentityLifecycleController.class})
public class IdentityLifecycleApiExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(IdentityLifecycleApiExceptionHandler.class);

    /**
     * Handles safe lifecycle authentication failures.
     */
    @ExceptionHandler(IdentityApiException.class)
    public ResponseEntity<ProblemDetail> handleIdentityApiException(IdentityApiException exception) {
        return problem(exception.status(), exception.code(), exception.getMessage());
    }

    /**
     * Handles malformed lifecycle requests.
     */
    @ExceptionHandler({HttpMessageNotReadableException.class, IllegalArgumentException.class})
    public ResponseEntity<ProblemDetail> handleBadRequest() {
        return problem(HttpStatus.BAD_REQUEST, "IDENTITY_INVALID_REQUEST", "Identity request is invalid.");
    }

    /**
     * Handles persistence failures with a generic safe error.
     */
    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ProblemDetail> handleDataAccessException() {
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "IDENTITY_OPERATION_FAILED", "Identity operation failed.");
    }

    /**
     * Handles missing credential-issuance configuration.
     */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ProblemDetail> handleUnavailableIdentityOperation(IllegalStateException exception) {
        LOGGER.error("Identity lifecycle operation is unavailable: {}", exception.getMessage(), exception);
        return problem(
                HttpStatus.SERVICE_UNAVAILABLE,
                "IDENTITY_OPERATION_UNAVAILABLE",
                "Identity operation is unavailable.");
    }

    private ResponseEntity<ProblemDetail> problem(HttpStatus status, String code, String detail) {
        var problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(status.getReasonPhrase());
        problem.setProperty("code", code);
        problem.setProperty("requestId", UUID.randomUUID().toString());
        return ResponseEntity.status(status).body(problem);
    }
}
