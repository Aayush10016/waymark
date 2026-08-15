package io.github.aayush10016.trail.backgroundjob.config;

import io.github.aayush10016.trail.backgroundjob.adapter.in.scheduler.BackgroundJobSchedulerProperties;

/**
 * Mutable Spring-bound scheduler configuration for background jobs.
 */
public class BackgroundJobSchedulerConfigurationProperties {

    private boolean enabled = true;
    private String leaseOwner = "trail-backend-local";
    private long pollIntervalMilliseconds = 30_000L;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getLeaseOwner() {
        return leaseOwner;
    }

    public void setLeaseOwner(String leaseOwner) {
        this.leaseOwner = leaseOwner;
    }

    public long getPollIntervalMilliseconds() {
        return pollIntervalMilliseconds;
    }

    public void setPollIntervalMilliseconds(long pollIntervalMilliseconds) {
        this.pollIntervalMilliseconds = pollIntervalMilliseconds;
    }

    BackgroundJobSchedulerProperties toProperties() {
        return new BackgroundJobSchedulerProperties(enabled, leaseOwner, pollIntervalMilliseconds);
    }
}
