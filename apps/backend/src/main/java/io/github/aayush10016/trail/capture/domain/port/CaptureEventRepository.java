package io.github.aayush10016.trail.capture.domain.port;

import io.github.aayush10016.trail.capture.domain.model.CaptureEventToPersist;
import io.github.aayush10016.trail.identity.domain.model.AuthenticatedPrincipal;

/**
 * Persists sanitized capture events.
 */
public interface CaptureEventRepository {

    /**
     * Inserts a sanitized capture event unless the user already submitted the same client event ID.
     *
     * @param principal authenticated owner
     * @param event sanitized event
     * @return insert outcome
     */
    StoreCaptureEventResult storeIfAbsent(AuthenticatedPrincipal principal, CaptureEventToPersist event);

    /**
     * Capture event insert outcome.
     */
    enum StoreCaptureEventResult {
        /**
         * Event was inserted.
         */
        INSERTED,

        /**
         * Event already existed for the same user and client event ID.
         */
        DUPLICATE
    }
}
