package io.github.aayush10016.trail.intelligence.application.service;

import java.util.Objects;

/**
 * Content-free enrichment job result consumed by the background job processor.
 */
public record ProblemSessionEnrichmentJobResult(ProblemSessionEnrichmentJobStatus status, String safeFailureCode) {

    /**
     * Creates a validated job result.
     */
    public ProblemSessionEnrichmentJobResult {
        Objects.requireNonNull(status, "status");
        if (status == ProblemSessionEnrichmentJobStatus.SUCCEEDED && safeFailureCode != null) {
            throw new IllegalArgumentException("successful enrichment must not have a failure code");
        }
        if (status != ProblemSessionEnrichmentJobStatus.SUCCEEDED
                && (safeFailureCode == null || safeFailureCode.isBlank())) {
            throw new IllegalArgumentException("failed enrichment requires a safe failure code");
        }
        if (safeFailureCode != null) {
            safeFailureCode = safeFailureCode.strip();
        }
    }

    /**
     * Creates a successful result.
     */
    public static ProblemSessionEnrichmentJobResult succeeded() {
        return new ProblemSessionEnrichmentJobResult(ProblemSessionEnrichmentJobStatus.SUCCEEDED, null);
    }

    /**
     * Creates a retryable failure.
     */
    public static ProblemSessionEnrichmentJobResult retryableFailure(String safeFailureCode) {
        return new ProblemSessionEnrichmentJobResult(
                ProblemSessionEnrichmentJobStatus.RETRYABLE_FAILURE, safeFailureCode);
    }

    /**
     * Creates a terminal failure.
     */
    public static ProblemSessionEnrichmentJobResult terminalFailure(String safeFailureCode) {
        return new ProblemSessionEnrichmentJobResult(
                ProblemSessionEnrichmentJobStatus.TERMINAL_FAILURE, safeFailureCode);
    }
}
