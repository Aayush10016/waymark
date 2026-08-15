package io.github.aayush10016.trail.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.availability.ApplicationAvailability;
import org.springframework.boot.availability.ReadinessState;

class RuntimeReadinessBenchmarkTest {

    private static final Duration RENDER_READINESS_TARGET = Duration.ofSeconds(30);

    @Test
    void backendBecomesReadyWithinRenderStartupTargetWithoutProviderCalls() {
        var started = System.nanoTime();

        try (var context = new SpringApplicationBuilder(TrailApplication.class)
                .web(WebApplicationType.SERVLET)
                .properties(
                        "server.port=0",
                        "spring.main.banner-mode=off",
                        "spring.task.scheduling.enabled=false",
                        "trail.background-jobs.scheduler.enabled=false",
                        "trail.ai.provider=mock",
                        "trail.embedding.provider=mock")
                .run("--trail.background-jobs.scheduler.enabled=false")) {
            var elapsed = Duration.ofNanos(System.nanoTime() - started);
            var availability = context.getBean(ApplicationAvailability.class);

            assertThat(availability.getReadinessState()).isEqualTo(ReadinessState.ACCEPTING_TRAFFIC);
            assertThat(elapsed).isLessThan(RENDER_READINESS_TARGET);
            assertThat(Runtime.getRuntime().maxMemory()).isPositive();
        }
    }
}
