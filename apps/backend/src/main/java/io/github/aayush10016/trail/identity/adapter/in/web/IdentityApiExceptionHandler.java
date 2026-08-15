package io.github.aayush10016.trail.identity.adapter.in.web;

import io.github.aayush10016.trail.identity.application.service.AccountDeletionReauthenticationException;
import java.util.UUID;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Maps Identity API failures to RFC 9457 problem details without sensitive content.
 */
@RestControllerAdvice(assignableTypes = {AccountDeletionController.class})
public class IdentityApiExceptionHandler {

    /**
     * Handles safe Identity API failures.
     */
    @ExceptionHandler(IdentityApiException.class)
    public ResponseEntity<ProblemDetail> handleIdentityApiException(IdentityApiException exception) {
        return problem(exception.status(), exception.code(), exception.getMessage());
    }

    /**
     * Handles failed account deletion reauthentication.
     */
    @ExceptionHandler(AccountDeletionReauthenticationException.class)
    public ResponseEntity<ProblemDetail> handleReauthenticationFailure() {
        return problem(HttpStatus.FORBIDDEN, "ACCOUNT_REAUTHENTICATION_FAILED", "Account deletion was rejected.");
    }

    /**
     * Handles malformed account deletion requests.
     */
    @ExceptionHandler({HttpMessageNotReadableException.class, IllegalArgumentException.class})
    public ResponseEntity<ProblemDetail> handleBadRequest() {
        return problem(HttpStatus.BAD_REQUEST, "ACCOUNT_DELETION_INVALID_REQUEST", "Account deletion request is invalid.");
    }

    /**
     * Handles persistence failures with a generic safe error.
     */
    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ProblemDetail> handleDataAccessException() {
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "ACCOUNT_DELETION_FAILED", "Account deletion failed.");
    }

    private ResponseEntity<ProblemDetail> problem(HttpStatus status, String code, String detail) {
        var problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(status.getReasonPhrase());
        problem.setProperty("code", code);
        problem.setProperty("requestId", UUID.randomUUID().toString());
        return ResponseEntity.status(status).body(problem);
    }
}
