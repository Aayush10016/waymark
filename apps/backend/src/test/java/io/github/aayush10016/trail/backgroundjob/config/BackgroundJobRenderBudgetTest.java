package io.github.aayush10016.trail.backgroundjob.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class BackgroundJobRenderBudgetTest {

    @Test
    void defaultBackgroundJobPolicyIsBoundedForSharedRenderWebRuntime() {
        var properties = new BackgroundJobProcessingProperties();
        var policy = properties.toPolicy();

        assertThat(policy.batchSize()).isEqualTo(10);
        assertThat(policy.leaseDuration()).isEqualTo(Duration.ofMinutes(5));
        assertThat(policy.maxAttempts()).isEqualTo(5);
        assertThat(policy.initialRetryBackoff()).isEqualTo(Duration.ofSeconds(30));
        assertThat(policy.maxRetryBackoff()).isEqualTo(Duration.ofMinutes(30));
    }
}
