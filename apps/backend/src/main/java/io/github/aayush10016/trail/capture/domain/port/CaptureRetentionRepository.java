package io.github.aayush10016.trail.capture.domain.port;

import java.time.Instant;

/**
 * Expires short-lived raw capture data.
 */
public interface CaptureRetentionRepository {

    /**
     * Expires at most {@code batchSize} raw capture events whose retention window has passed.
     *
     * @param expiresAtOrBefore expiry cutoff
     * @param batchSize maximum rows to expire
     * @return number of rows expired
     */
    int expireRawCaptureEvents(Instant expiresAtOrBefore, int batchSize);
}
