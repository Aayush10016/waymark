package io.github.aayush10016.trail.problem.application.dto;

import io.github.aayush10016.trail.problem.domain.model.SessionArtifact;
import java.time.Instant;
import java.util.UUID;

/**
 * Privacy-bounded durable Problem Session artifact detail.
 *
 * @param id artifact ID
 * @param position stable active position
 * @param artifactType artifact category
 * @param sanitizedUrl optional sanitized HTTP(S) URL
 * @param hostname optional normalized hostname
 * @param title optional bounded title
 * @param queryText optional allowlisted bounded query text
 * @param occurredAt artifact occurrence time
 * @param decisive whether user marked the artifact decisive
 */
public record SessionArtifactDetailResponse(
        UUID id,
        int position,
        String artifactType,
        String sanitizedUrl,
        String hostname,
        String title,
        String queryText,
        Instant occurredAt,
        boolean decisive) {

    /**
     * Builds a response from a durable artifact snapshot.
     */
    public static SessionArtifactDetailResponse from(SessionArtifact artifact) {
        return new SessionArtifactDetailResponse(
                artifact.id(),
                artifact.position(),
                artifact.artifactType().name(),
                artifact.sanitizedUrl(),
                artifact.hostname(),
                artifact.title(),
                artifact.queryText(),
                artifact.occurredAt(),
                artifact.decisive());
    }
}
