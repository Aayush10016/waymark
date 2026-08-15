package io.github.aayush10016.trail.backgroundjob.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.aayush10016.trail.backgroundjob.adapter.in.scheduler.BackgroundJobScheduler;
import io.github.aayush10016.trail.bootstrap.TrailApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(
        classes = TrailApplication.class,
        properties = {
            "trail.background-jobs.scheduler.enabled=false",
            "trail.background-jobs.scheduler.lease-owner=render-readiness-test",
            "trail.background-jobs.scheduler.poll-interval-milliseconds=60000"
        })
class BackgroundJobSchedulerConfigurationTest {

    @Autowired
    private BackgroundJobSchedulerConfigurationProperties properties;

    @Autowired
    private ApplicationContext context;

    @Test
    void bindsSchedulerPropertiesAndDoesNotCreateScheduledBeanWhenDisabled() {
        var schedulerProperties = properties.toProperties();

        assertThat(schedulerProperties.enabled()).isFalse();
        assertThat(schedulerProperties.leaseOwner()).isEqualTo("render-readiness-test");
        assertThat(schedulerProperties.pollIntervalMilliseconds()).isEqualTo(60_000L);
        assertThat(context.getBeansOfType(BackgroundJobScheduler.class)).isEmpty();
    }
}
