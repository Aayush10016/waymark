package io.github.aayush10016.trail.backgroundjob.application.service;

import java.util.Objects;

/**
 * Handler outcome translated into durable background job state.
 */
public record BackgroundJobHandlingResult(BackgroundJobHandlingStatus status, String safeFailureCode) {

    /**
     * Creates a validated handling result.
     */
    public BackgroundJobHandlingResult {
        Objects.requireNonNull(status, "status");
        if (status == BackgroundJobHandlingStatus.SUCCEEDED && safeFailureCode != null) {
            throw new IllegalArgumentException("successful jobs must not include a failure code");
        }
        if (status != BackgroundJobHandlingStatus.SUCCEEDED) {
            if (safeFailureCode == null || safeFailureCode.isBlank()) {
                throw new IllegalArgumentException("failed jobs require a safe failure code");
            }
            if (safeFailureCode.strip().length() > 128) {
                throw new IllegalArgumentException("safe failure code must be bounded");
            }
            safeFailureCode = safeFailureCode.strip();
        }
    }

    /**
     * Successful handling result.
     */
    public static BackgroundJobHandlingResult succeeded() {
        return new BackgroundJobHandlingResult(BackgroundJobHandlingStatus.SUCCEEDED, null);
    }

    /**
     * Retryable handling result.
     */
    public static BackgroundJobHandlingResult retryableFailure(String safeFailureCode) {
        return new BackgroundJobHandlingResult(BackgroundJobHandlingStatus.RETRYABLE_FAILURE, safeFailureCode);
    }

    /**
     * Terminal handling result.
     */
    public static BackgroundJobHandlingResult terminalFailure(String safeFailureCode) {
        return new BackgroundJobHandlingResult(BackgroundJobHandlingStatus.TERMINAL_FAILURE, safeFailureCode);
    }
}
