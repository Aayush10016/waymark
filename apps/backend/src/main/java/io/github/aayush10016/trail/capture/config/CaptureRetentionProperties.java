package io.github.aayush10016.trail.capture.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configures raw capture retention job chunking.
 */
@ConfigurationProperties(prefix = "trail.capture.retention")
public class CaptureRetentionProperties {

    private int batchSize = 500;
    private int maxBatches = 20;

    public int batchSize() {
        return batchSize;
    }

    public void setBatchSize(int batchSize) {
        if (batchSize <= 0) {
            throw new IllegalArgumentException("batchSize must be positive");
        }
        this.batchSize = batchSize;
    }

    public int maxBatches() {
        return maxBatches;
    }

    public void setMaxBatches(int maxBatches) {
        if (maxBatches <= 0) {
            throw new IllegalArgumentException("maxBatches must be positive");
        }
        this.maxBatches = maxBatches;
    }
}
