package io.github.aayush10016.trail.capture.adapter.in.web;

import org.springframework.http.HttpStatus;

/**
 * Safe HTTP exception for capture API failures.
 */
public final class CaptureApiException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final HttpStatus status;
    private final String code;

    private CaptureApiException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    /**
     * Creates an unauthorized exception.
     */
    public static CaptureApiException unauthorized() {
        return new CaptureApiException(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED", "Authentication required.");
    }

    /**
     * Creates a bad request exception.
     */
    public static CaptureApiException badRequest(String code, String message) {
        return new CaptureApiException(HttpStatus.BAD_REQUEST, code, message);
    }

    /**
     * Returns the HTTP status.
     */
    public HttpStatus status() {
        return status;
    }

    /**
     * Returns the stable safe error code.
     */
    public String code() {
        return code;
    }
}
