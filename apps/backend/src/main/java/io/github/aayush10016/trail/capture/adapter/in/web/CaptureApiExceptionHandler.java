package io.github.aayush10016.trail.capture.adapter.in.web;

import io.github.aayush10016.trail.capture.application.service.CaptureBatchTooLargeException;
import io.github.aayush10016.trail.capture.application.service.InvalidCaptureConsentDecisionException;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Maps capture API failures to RFC 9457 problem details without sensitive content.
 */
@RestControllerAdvice(assignableTypes = {
    CaptureBatchController.class, CaptureConsentController.class, CaptureStateController.class
})
public class CaptureApiExceptionHandler {

    /**
     * Handles safe capture API exceptions.
     */
    @ExceptionHandler(CaptureApiException.class)
    public ResponseEntity<ProblemDetail> handleCaptureApiException(CaptureApiException exception) {
        return problem(exception.status(), exception.code(), exception.getMessage());
    }

    /**
     * Handles malformed request bodies.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ProblemDetail> handleNotReadable() {
        return problem(HttpStatus.BAD_REQUEST, "CAPTURE_INVALID_REQUEST", "Capture request is invalid.");
    }

    /**
     * Handles oversized capture batches.
     */
    @ExceptionHandler(CaptureBatchTooLargeException.class)
    public ResponseEntity<ProblemDetail> handleTooLarge(CaptureBatchTooLargeException exception) {
        var detail = "Capture batch may contain at most " + exception.maximumBatchSize() + " events.";
        return problem(HttpStatus.PAYLOAD_TOO_LARGE, "CAPTURE_BATCH_TOO_LARGE", detail);
    }

    /**
     * Handles malformed or unsupported consent decisions.
     */
    @ExceptionHandler(InvalidCaptureConsentDecisionException.class)
    public ResponseEntity<ProblemDetail> handleInvalidConsentDecision(InvalidCaptureConsentDecisionException exception) {
        return problem(HttpStatus.BAD_REQUEST, "CAPTURE_CONSENT_INVALID_REQUEST", exception.getMessage());
    }

    private ResponseEntity<ProblemDetail> problem(HttpStatus status, String code, String detail) {
        var problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(status.getReasonPhrase());
        problem.setProperty("code", code);
        problem.setProperty("requestId", UUID.randomUUID().toString());
        return ResponseEntity.status(status).body(problem);
    }
}
