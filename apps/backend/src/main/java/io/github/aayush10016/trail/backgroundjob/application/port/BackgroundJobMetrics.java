package io.github.aayush10016.trail.backgroundjob.application.port;

import io.github.aayush10016.trail.backgroundjob.domain.model.BackgroundJobType;

/**
 * Observability boundary for background job processing metrics.
 */
public interface BackgroundJobMetrics {

    /**
     * Records a successful handler execution.
     */
    void recordSucceeded(BackgroundJobType jobType);

    /**
     * Records a retryable handler execution failure.
     */
    void recordRetryableFailure(BackgroundJobType jobType);

    /**
     * Records a terminal handler execution failure.
     */
    void recordTerminalFailure(BackgroundJobType jobType);
}
