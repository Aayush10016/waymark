package io.github.aayush10016.trail.capture.application.dto;

/**
 * Per-event capture ingestion result.
 *
 * @param clientEventId stable client event identifier when supplied
 * @param status event status
 * @param rejectionCode stable rejection code when rejected
 */
public record CaptureEventIngestionResult(
        String clientEventId, CaptureEventIngestionStatus status, CaptureEventRejectionCode rejectionCode) {

    /**
     * Creates an accepted result.
     */
    public static CaptureEventIngestionResult accepted(String clientEventId) {
        return new CaptureEventIngestionResult(clientEventId, CaptureEventIngestionStatus.ACCEPTED, null);
    }

    /**
     * Creates a duplicate result.
     */
    public static CaptureEventIngestionResult duplicate(String clientEventId) {
        return new CaptureEventIngestionResult(clientEventId, CaptureEventIngestionStatus.DUPLICATE, null);
    }

    /**
     * Creates a rejected result.
     */
    public static CaptureEventIngestionResult rejected(String clientEventId, CaptureEventRejectionCode rejectionCode) {
        return new CaptureEventIngestionResult(clientEventId, CaptureEventIngestionStatus.REJECTED, rejectionCode);
    }
}
