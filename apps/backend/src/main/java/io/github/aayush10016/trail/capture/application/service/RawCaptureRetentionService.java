package io.github.aayush10016.trail.capture.application.service;

import io.github.aayush10016.trail.capture.application.dto.RawCaptureRetentionResult;
import io.github.aayush10016.trail.capture.domain.port.CaptureRetentionRepository;
import java.time.Clock;
import java.util.Objects;

/**
 * Application use case for expiring short-lived raw capture events.
 */
public class RawCaptureRetentionService {

    private final Clock clock;
    private final CaptureRetentionRepository retentionRepository;

    /**
     * Creates the raw capture retention service.
     *
     * @param retentionRepository raw capture retention repository
     * @param clock clock used to calculate expiry cutoff
     */
    public RawCaptureRetentionService(CaptureRetentionRepository retentionRepository, Clock clock) {
        this.retentionRepository = Objects.requireNonNull(retentionRepository, "retentionRepository");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * Expires raw capture events in bounded chunks.
     *
     * @param batchSize maximum rows deleted per database statement
     * @param maxBatches maximum statements executed in this job run
     * @return retention job result
     */
    public RawCaptureRetentionResult expireExpiredRawCaptureEvents(int batchSize, int maxBatches) {
        validatePositive(batchSize, "batchSize");
        validatePositive(maxBatches, "maxBatches");

        var cutoff = clock.instant();
        var expiredEventCount = 0;
        var batchCount = 0;
        var complete = true;

        while (batchCount < maxBatches) {
            var expiredInBatch = retentionRepository.expireRawCaptureEvents(cutoff, batchSize);
            if (expiredInBatch == 0) {
                break;
            }

            expiredEventCount += expiredInBatch;
            batchCount += 1;

            if (expiredInBatch < batchSize) {
                break;
            }

            if (batchCount == maxBatches) {
                complete = false;
            }
        }

        return new RawCaptureRetentionResult(expiredEventCount, batchCount, complete);
    }

    private static void validatePositive(int value, String name) {
        if (value <= 0) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }
}
