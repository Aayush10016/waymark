package io.github.aayush10016.trail.capture.domain.model;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Sanitized capture event ready for database persistence.
 *
 * @param clientEventId stable extension-generated event identifier
 * @param clientSequence optional extension sequence
 * @param clientInstanceId stable extension installation/session identifier
 * @param tabSessionId optional tab-session identifier
 * @param parentClientEventId optional parent client event identifier
 * @param problemSessionId optional owning candidate Problem Session
 * @param source browser event source
 * @param resourceType classified resource type
 * @param navigationType optional browser navigation transition type
 * @param canonicalUrl sanitized canonical URL
 * @param hostname sanitized hostname
 * @param title bounded sanitized title
 * @param searchEngine allowlisted search engine
 * @param searchQuery allowlisted search query
 * @param removedQueryParameters removed query parameter names
 * @param transitionQualifiers browser transition qualifiers
 * @param policyVersion sanitation policy version
 * @param browserEventTimeMilliseconds browser event timestamp when supplied by Chrome
 * @param occurredAt event occurrence time
 * @param retentionExpiresAt raw event retention expiry
 */
public record CaptureEventToPersist(
        String clientEventId,
        Long clientSequence,
        String clientInstanceId,
        String tabSessionId,
        String parentClientEventId,
        UUID problemSessionId,
        String source,
        String resourceType,
        String navigationType,
        String canonicalUrl,
        String hostname,
        String title,
        String searchEngine,
        String searchQuery,
        List<String> removedQueryParameters,
        List<String> transitionQualifiers,
        String policyVersion,
        Double browserEventTimeMilliseconds,
        Instant occurredAt,
        Instant retentionExpiresAt) {

    /**
     * Creates an immutable event persistence payload.
     */
    public CaptureEventToPersist {
        clientEventId = requiredText("clientEventId", clientEventId);
        clientInstanceId = requiredText("clientInstanceId", clientInstanceId);
        source = requiredText("source", source);
        resourceType = requiredText("resourceType", resourceType);
        canonicalUrl = requiredText("canonicalUrl", canonicalUrl);
        hostname = requiredText("hostname", hostname);
        policyVersion = requiredText("policyVersion", policyVersion);
        Objects.requireNonNull(occurredAt, "occurredAt");
        Objects.requireNonNull(retentionExpiresAt, "retentionExpiresAt");
        removedQueryParameters = List.copyOf(removedQueryParameters);
        transitionQualifiers = List.copyOf(transitionQualifiers);
    }

    private static String requiredText(String fieldName, String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.strip();
    }
}
