package io.github.aayush10016.trail.capture.domain.model;

/**
 * Raw capture candidate before backend sanitation.
 *
 * @param url raw URL received at the sanitation boundary
 * @param title optional raw title received at the sanitation boundary
 */
public record RawCaptureCandidate(String url, String title) {

    /**
     * Creates a raw capture candidate.
     */
    public RawCaptureCandidate {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("url must not be blank");
        }
    }
}
