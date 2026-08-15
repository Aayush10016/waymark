package io.github.aayush10016.trail.capture.application.dto;

import java.util.List;

/**
 * Result of processing a capture batch.
 *
 * @param results per-event ingestion results
 */
public record CaptureBatchIngestionResult(List<CaptureEventIngestionResult> results) {

    /**
     * Creates an immutable batch result.
     */
    public CaptureBatchIngestionResult {
        results = List.copyOf(results);
    }
}
