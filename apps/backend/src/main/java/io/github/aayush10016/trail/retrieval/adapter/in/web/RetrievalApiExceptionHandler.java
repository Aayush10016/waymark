package io.github.aayush10016.trail.retrieval.adapter.in.web;

import io.github.aayush10016.trail.retrieval.application.service.ProblemRecommendationNotFoundException;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Maps retrieval API failures to RFC 9457 problem details without sensitive content.
 */
@RestControllerAdvice(
        assignableTypes = {
            ProblemSessionSearchController.class,
            ProblemSessionRecommendationController.class,
            RecommendationEventController.class
        })
public class RetrievalApiExceptionHandler {

    /**
     * Handles safe retrieval API exceptions.
     */
    @ExceptionHandler(RetrievalApiException.class)
    public ResponseEntity<ProblemDetail> handleRetrievalApiException(RetrievalApiException exception) {
        return problem(exception.status(), exception.code(), exception.getMessage());
    }

    /**
     * Handles missing or cross-user recommendation targets.
     */
    @ExceptionHandler(ProblemRecommendationNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleRecommendationNotFound() {
        return problem(HttpStatus.NOT_FOUND, "RECOMMENDATION_TARGET_NOT_FOUND", "Resource not found.");
    }

    /**
     * Handles malformed request bodies.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ProblemDetail> handleNotReadable() {
        return problem(HttpStatus.BAD_REQUEST, "SEARCH_INVALID_REQUEST", "Search request is invalid.");
    }

    /**
     * Handles semantic request validation failures from the application layer.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ProblemDetail> handleIllegalArgument(IllegalArgumentException exception) {
        return problem(HttpStatus.BAD_REQUEST, "SEARCH_INVALID_REQUEST", "Search request is invalid.");
    }

    private ResponseEntity<ProblemDetail> problem(HttpStatus status, String code, String detail) {
        var problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(status.getReasonPhrase());
        problem.setProperty("code", code);
        problem.setProperty("requestId", UUID.randomUUID().toString());
        return ResponseEntity.status(status).body(problem);
    }
}
