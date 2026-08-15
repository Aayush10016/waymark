package io.github.aayush10016.trail.backgroundjob.config;

import io.github.aayush10016.trail.backgroundjob.adapter.in.scheduler.BackgroundJobScheduler;
import io.github.aayush10016.trail.backgroundjob.adapter.out.observability.MicrometerBackgroundJobMetrics;
import io.github.aayush10016.trail.backgroundjob.application.port.BackgroundJobHandler;
import io.github.aayush10016.trail.backgroundjob.application.port.BackgroundJobMetrics;
import io.github.aayush10016.trail.backgroundjob.application.service.BackgroundJobProcessingPolicy;
import io.github.aayush10016.trail.backgroundjob.application.service.BackgroundJobProcessorService;
import io.github.aayush10016.trail.backgroundjob.domain.port.BackgroundJobRepository;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.util.Collection;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Wires the durable background job processing capability.
 */
@Configuration
@EnableScheduling
class BackgroundJobConfiguration {

    @Bean
    BackgroundJobProcessorService backgroundJobProcessorService(
            BackgroundJobRepository repository,
            Collection<BackgroundJobHandler> handlers,
            BackgroundJobProcessingProperties processingProperties,
            Clock clock,
            BackgroundJobMetrics metrics) {
        return new BackgroundJobProcessorService(
                repository,
                handlers,
                processingProperties.toPolicy(),
                clock,
                metrics);
    }

    @Bean
    BackgroundJobMetrics backgroundJobMetrics(MeterRegistry meterRegistry) {
        return new MicrometerBackgroundJobMetrics(meterRegistry);
    }

    @Bean
    @ConditionalOnProperty(prefix = "trail.background-jobs.scheduler", name = "enabled", havingValue = "true", matchIfMissing = true)
    BackgroundJobScheduler backgroundJobScheduler(
            BackgroundJobProcessorService processorService,
            BackgroundJobSchedulerConfigurationProperties schedulerProperties) {
        return new BackgroundJobScheduler(processorService, schedulerProperties.toProperties());
    }

    @Bean
    @ConfigurationProperties("trail.background-jobs.processing")
    BackgroundJobProcessingProperties backgroundJobProcessingProperties() {
        return new BackgroundJobProcessingProperties();
    }

    @Bean
    @ConfigurationProperties("trail.background-jobs.scheduler")
    BackgroundJobSchedulerConfigurationProperties backgroundJobSchedulerConfigurationProperties() {
        return new BackgroundJobSchedulerConfigurationProperties();
    }

}
