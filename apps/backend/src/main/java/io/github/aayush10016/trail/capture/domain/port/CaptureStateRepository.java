package io.github.aayush10016.trail.capture.domain.port;

import io.github.aayush10016.trail.capture.domain.model.CurrentCaptureCandidate;
import java.util.Optional;
import java.util.UUID;

/**
 * Port for backend capture-state candidate reads.
 */
public interface CaptureStateRepository {

    /**
     * Finds the latest current candidate or reviewable Problem Session for the user.
     */
    Optional<CurrentCaptureCandidate> findLatestCurrentCandidate(UUID userId);
}
