package io.github.aayush10016.trail.capture.application.command;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Command payload for one capture event in a batch.
 *
 * @param clientEventId stable client event identifier
 * @param clientSequence optional client sequence
 * @param tabSessionId optional tab-session identifier
 * @param parentClientEventId optional parent event identifier
 * @param problemSessionId optional Problem Session active when this event was captured
 * @param source browser capture source
 * @param resourceType resource classification from the extension
 * @param navigationType optional navigation transition type
 * @param url captured URL to validate and sanitize
 * @param title captured title to validate and sanitize
 * @param browserEventTimeMilliseconds optional browser event timestamp
 * @param occurredAt event occurrence time
 * @param transitionQualifiers optional browser transition qualifiers
 */
public record IngestCaptureEventCommand(
        String clientEventId,
        Long clientSequence,
        String tabSessionId,
        String parentClientEventId,
        UUID problemSessionId,
        CaptureEventSource source,
        CaptureResourceType resourceType,
        String navigationType,
        String url,
        String title,
        Double browserEventTimeMilliseconds,
        Instant occurredAt,
        List<String> transitionQualifiers) {

    /**
     * Creates an immutable event command.
     */
    public IngestCaptureEventCommand {
        transitionQualifiers = transitionQualifiers == null ? List.of() : List.copyOf(transitionQualifiers);
    }
}
