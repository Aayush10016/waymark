package io.github.aayush10016.trail.capture.adapter.in.job;

import io.github.aayush10016.trail.capture.application.dto.RawCaptureRetentionResult;
import io.github.aayush10016.trail.capture.application.service.RawCaptureRetentionService;
import io.github.aayush10016.trail.capture.config.CaptureRetentionProperties;
import java.util.Objects;
import org.springframework.stereotype.Component;

/**
 * Inbound job adapter for expiring short-lived raw capture rows.
 */
@Component
public class RawCaptureRetentionJob {

    private final CaptureRetentionProperties properties;
    private final RawCaptureRetentionService service;

    /**
     * Creates the retention job adapter.
     */
    public RawCaptureRetentionJob(RawCaptureRetentionService service, CaptureRetentionProperties properties) {
        this.service = Objects.requireNonNull(service, "service");
        this.properties = Objects.requireNonNull(properties, "properties");
    }

    /**
     * Runs one bounded retention pass.
     *
     * @return retention job result
     */
    public RawCaptureRetentionResult runOnce() {
        return service.expireExpiredRawCaptureEvents(properties.batchSize(), properties.maxBatches());
    }
}
