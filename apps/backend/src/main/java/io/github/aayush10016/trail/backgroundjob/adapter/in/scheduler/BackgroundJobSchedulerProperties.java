package io.github.aayush10016.trail.backgroundjob.adapter.in.scheduler;

/**
 * Scheduler configuration for the local background job runner.
 */
public record BackgroundJobSchedulerProperties(boolean enabled, String leaseOwner, long pollIntervalMilliseconds) {

    /**
     * Creates validated scheduler properties.
     */
    public BackgroundJobSchedulerProperties {
        if (leaseOwner == null || leaseOwner.isBlank()) {
            throw new IllegalArgumentException("leaseOwner must not be blank");
        }
        leaseOwner = leaseOwner.strip();
        if (leaseOwner.length() > 128) {
            throw new IllegalArgumentException("leaseOwner must be bounded");
        }
        if (pollIntervalMilliseconds <= 0) {
            throw new IllegalArgumentException("pollIntervalMilliseconds must be positive");
        }
    }
}
