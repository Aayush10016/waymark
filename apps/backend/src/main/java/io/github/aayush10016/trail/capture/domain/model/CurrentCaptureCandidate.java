package io.github.aayush10016.trail.capture.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Bounded current Problem Session candidate summary for capture-state reads.
 */
public record CurrentCaptureCandidate(
        UUID problemSessionId, String status, String title, Instant startedAt, Instant updatedAt) {

    /**
     * Creates a validated candidate summary.
     */
    public CurrentCaptureCandidate {
        Objects.requireNonNull(problemSessionId, "problemSessionId");
        status = requireText(status, "status");
        title = title == null || title.isBlank() ? null : title.strip();
        Objects.requireNonNull(startedAt, "startedAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.strip();
    }
}
