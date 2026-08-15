package io.github.aayush10016.trail.identity.adapter.in.web;

import org.springframework.http.HttpStatus;

/**
 * Safe Identity API exception with stable error code.
 */
public class IdentityApiException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final HttpStatus status;
    private final String code;

    private IdentityApiException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    /**
     * Creates an authentication failure.
     */
    public static IdentityApiException unauthorized() {
        return new IdentityApiException(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED", "Authentication is required.");
    }

    HttpStatus status() {
        return status;
    }

    String code() {
        return code;
    }
}
