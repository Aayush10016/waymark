package io.github.aayush10016.trail.backgroundjob.adapter.out.observability;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.aayush10016.trail.backgroundjob.domain.model.BackgroundJobType;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

class MicrometerBackgroundJobMetricsTest {

    @Test
    void recordsOutcomeCountersWithBoundedTags() {
        var registry = new SimpleMeterRegistry();
        var metrics = new MicrometerBackgroundJobMetrics(registry);

        metrics.recordSucceeded(BackgroundJobType.PROBLEM_SESSION_ENRICHMENT);
        metrics.recordRetryableFailure(BackgroundJobType.PROBLEM_SESSION_ENRICHMENT);
        metrics.recordTerminalFailure(BackgroundJobType.PROBLEM_SESSION_ENRICHMENT);

        assertThat(counter(registry, "succeeded")).isEqualTo(1.0d);
        assertThat(counter(registry, "retryable_failure")).isEqualTo(1.0d);
        assertThat(counter(registry, "terminal_failure")).isEqualTo(1.0d);
    }

    private static double counter(SimpleMeterRegistry registry, String outcome) {
        return registry.get("trail.background_jobs.processed")
                .tag("job_type", BackgroundJobType.PROBLEM_SESSION_ENRICHMENT.name())
                .tag("outcome", outcome)
                .counter()
                .count();
    }
}
