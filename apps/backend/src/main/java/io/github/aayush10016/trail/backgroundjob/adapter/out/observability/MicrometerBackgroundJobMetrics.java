package io.github.aayush10016.trail.backgroundjob.adapter.out.observability;

import io.github.aayush10016.trail.backgroundjob.application.port.BackgroundJobMetrics;
import io.github.aayush10016.trail.backgroundjob.domain.model.BackgroundJobType;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Objects;

/**
 * Micrometer metrics adapter for background job outcomes.
 */
public class MicrometerBackgroundJobMetrics implements BackgroundJobMetrics {

    private final MeterRegistry meterRegistry;

    /**
     * Creates the metrics adapter.
     */
    public MicrometerBackgroundJobMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = Objects.requireNonNull(meterRegistry, "meterRegistry");
    }

    @Override
    public void recordSucceeded(BackgroundJobType jobType) {
        increment(jobType, "succeeded");
    }

    @Override
    public void recordRetryableFailure(BackgroundJobType jobType) {
        increment(jobType, "retryable_failure");
    }

    @Override
    public void recordTerminalFailure(BackgroundJobType jobType) {
        increment(jobType, "terminal_failure");
    }

    private void increment(BackgroundJobType jobType, String outcome) {
        Counter.builder("trail.background_jobs.processed")
                .description("Background job processing outcomes")
                .tag("job_type", jobType.name())
                .tag("outcome", outcome)
                .register(meterRegistry)
                .increment();
    }
}
