package io.github.aayush10016.trail.capture.application.command;

import java.util.List;
import java.util.Objects;

/**
 * Command to ingest one bounded extension capture batch.
 *
 * @param clientInstanceId stable extension instance identifier
 * @param events capture events in client order
 */
public record IngestCaptureBatchCommand(String clientInstanceId, List<IngestCaptureEventCommand> events) {

    /**
     * Creates an immutable capture batch command.
     */
    public IngestCaptureBatchCommand {
        Objects.requireNonNull(events, "events");
        events = List.copyOf(events);
    }
}
