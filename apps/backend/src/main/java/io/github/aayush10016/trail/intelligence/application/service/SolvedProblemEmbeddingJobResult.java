package io.github.aayush10016.trail.intelligence.application.service;

import java.util.Objects;

/**
 * Content-free result for one solved-session embedding job attempt.
 */
public record SolvedProblemEmbeddingJobResult(SolvedProblemEmbeddingJobStatus status, String safeFailureCode) {

    /**
     * Creates a validated result.
     */
    public SolvedProblemEmbeddingJobResult {
        Objects.requireNonNull(status, "status");
        if (status == SolvedProblemEmbeddingJobStatus.SUCCEEDED && safeFailureCode != null) {
            throw new IllegalArgumentException("successful result must not include a failure code");
        }
        if (status != SolvedProblemEmbeddingJobStatus.SUCCEEDED
                && (safeFailureCode == null || safeFailureCode.isBlank())) {
            throw new IllegalArgumentException("failed result requires a safe failure code");
        }
    }

    /**
     * Successful result.
     */
    public static SolvedProblemEmbeddingJobResult succeeded() {
        return new SolvedProblemEmbeddingJobResult(SolvedProblemEmbeddingJobStatus.SUCCEEDED, null);
    }

    /**
     * Retryable failure result.
     */
    public static SolvedProblemEmbeddingJobResult retryableFailure(String safeFailureCode) {
        return new SolvedProblemEmbeddingJobResult(
                SolvedProblemEmbeddingJobStatus.RETRYABLE_FAILURE, safeFailureCode);
    }

    /**
     * Terminal failure result.
     */
    public static SolvedProblemEmbeddingJobResult terminalFailure(String safeFailureCode) {
        return new SolvedProblemEmbeddingJobResult(
                SolvedProblemEmbeddingJobStatus.TERMINAL_FAILURE, safeFailureCode);
    }
}
