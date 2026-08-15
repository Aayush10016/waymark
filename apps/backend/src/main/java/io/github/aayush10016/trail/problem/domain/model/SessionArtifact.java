package io.github.aayush10016.trail.problem.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Durable ordered evidence artifact copied from sanitized capture or user input.
 *
 * @param id stable artifact identifier
 * @param problemSessionId owning Problem Session identifier
 * @param position stable display position within the session
 * @param artifactType artifact category
 * @param sanitizedUrl optional sanitized URL
 * @param hostname optional normalized hostname
 * @param title optional bounded title
 * @param queryText optional allowlisted query text
 * @param sourceCaptureEventId optional source raw capture identifier, retained only as provenance
 * @param occurredAt time the artifact occurred
 * @param decisive whether this artifact is marked decisive
 * @param decisiveSource optional source of decisive marking
 * @param removedAt optional removal timestamp
 */
public record SessionArtifact(
        UUID id,
        UUID problemSessionId,
        int position,
        SessionArtifactType artifactType,
        String sanitizedUrl,
        String hostname,
        String title,
        String queryText,
        UUID sourceCaptureEventId,
        Instant occurredAt,
        boolean decisive,
        String decisiveSource,
        Instant removedAt) {

    /**
     * Creates a validated artifact snapshot.
     */
    public SessionArtifact {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(problemSessionId, "problemSessionId");
        Objects.requireNonNull(artifactType, "artifactType");
        Objects.requireNonNull(occurredAt, "occurredAt");
        if (position <= 0) {
            throw new IllegalArgumentException("position must be positive");
        }
    }
}
