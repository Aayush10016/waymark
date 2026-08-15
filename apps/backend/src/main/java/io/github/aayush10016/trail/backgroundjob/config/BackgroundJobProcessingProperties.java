package io.github.aayush10016.trail.backgroundjob.config;

import io.github.aayush10016.trail.backgroundjob.application.service.BackgroundJobProcessingPolicy;
import java.time.Duration;

/**
 * Mutable Spring-bound processing configuration for background jobs.
 */
public class BackgroundJobProcessingProperties {

    private int batchSize = 10;
    private int maxAttempts = 5;
    private Duration leaseDuration = Duration.ofMinutes(5);
    private Duration initialRetryBackoff = Duration.ofSeconds(30);
    private Duration maxRetryBackoff = Duration.ofMinutes(30);

    public int getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public void setMaxAttempts(int maxAttempts) {
        this.maxAttempts = maxAttempts;
    }

    public Duration getLeaseDuration() {
        return leaseDuration;
    }

    public void setLeaseDuration(Duration leaseDuration) {
        this.leaseDuration = leaseDuration;
    }

    public Duration getInitialRetryBackoff() {
        return initialRetryBackoff;
    }

    public void setInitialRetryBackoff(Duration initialRetryBackoff) {
        this.initialRetryBackoff = initialRetryBackoff;
    }

    public Duration getMaxRetryBackoff() {
        return maxRetryBackoff;
    }

    public void setMaxRetryBackoff(Duration maxRetryBackoff) {
        this.maxRetryBackoff = maxRetryBackoff;
    }

    BackgroundJobProcessingPolicy toPolicy() {
        return new BackgroundJobProcessingPolicy(
                batchSize,
                maxAttempts,
                leaseDuration,
                initialRetryBackoff,
                maxRetryBackoff);
    }
}
