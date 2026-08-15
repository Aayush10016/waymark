package io.github.aayush10016.trail.retrieval.adapter.in.web;

import org.springframework.http.HttpStatus;

/**
 * Safe HTTP exception for retrieval API failures.
 */
public final class RetrievalApiException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final HttpStatus status;
    private final String code;

    private RetrievalApiException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    /**
     * Creates an unauthorized exception.
     */
    public static RetrievalApiException unauthorized() {
        return new RetrievalApiException(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED", "Authentication required.");
    }

    /**
     * Creates a bad request exception.
     */
    public static RetrievalApiException badRequest(String code, String message) {
        return new RetrievalApiException(HttpStatus.BAD_REQUEST, code, message);
    }

    /**
     * Creates a not found exception.
     */
    public static RetrievalApiException notFound() {
        return new RetrievalApiException(HttpStatus.NOT_FOUND, "RECOMMENDATION_TARGET_NOT_FOUND", "Resource not found.");
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
