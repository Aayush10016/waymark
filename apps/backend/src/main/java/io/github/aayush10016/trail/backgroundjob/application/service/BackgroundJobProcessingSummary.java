package io.github.aayush10016.trail.backgroundjob.application.service;

/**
 * Processing counts for one scheduler cycle.
 */
public record BackgroundJobProcessingSummary(int claimed, int succeeded, int retryable, int terminal) {

    /**
     * Empty cycle summary.
     */
    public static BackgroundJobProcessingSummary empty() {
        return new BackgroundJobProcessingSummary(0, 0, 0, 0);
    }
}
