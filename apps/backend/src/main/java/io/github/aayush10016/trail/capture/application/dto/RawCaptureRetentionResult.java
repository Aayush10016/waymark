package io.github.aayush10016.trail.capture.application.dto;

/**
 * Result of one raw capture retention job run.
 *
 * @param expiredEventCount number of raw capture rows expired
 * @param batchCount number of delete batches executed
 * @param complete true when no additional immediately expired rows were observed within the configured job budget
 */
public record RawCaptureRetentionResult(int expiredEventCount, int batchCount, boolean complete) {

    /**
     * Creates an immutable retention result.
     */
    public RawCaptureRetentionResult {
        if (expiredEventCount < 0) {
            throw new IllegalArgumentException("expiredEventCount must not be negative");
        }
        if (batchCount < 0) {
            throw new IllegalArgumentException("batchCount must not be negative");
        }
    }
}
