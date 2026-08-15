package io.github.aayush10016.trail.capture.application.service;

/**
 * Raised when a capture batch exceeds the documented maximum event count.
 */
public final class CaptureBatchTooLargeException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final int maximumBatchSize;

    /**
     * Creates the exception.
     *
     * @param maximumBatchSize maximum accepted event count
     */
    public CaptureBatchTooLargeException(int maximumBatchSize) {
        super("Capture batch exceeds maximum event count");
        this.maximumBatchSize = maximumBatchSize;
    }

    /**
     * Returns the maximum accepted event count.
     */
    public int maximumBatchSize() {
        return maximumBatchSize;
    }
}
