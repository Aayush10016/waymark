package io.github.aayush10016.trail.backgroundjob.adapter.in.scheduler;

import io.github.aayush10016.trail.backgroundjob.application.service.BackgroundJobProcessorService;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Bounded in-process scheduler for durable background jobs.
 */
public class BackgroundJobScheduler {

    private static final Logger LOGGER = LoggerFactory.getLogger(BackgroundJobScheduler.class);

    private final BackgroundJobProcessorService processorService;
    private final BackgroundJobSchedulerProperties properties;

    /**
     * Creates the scheduler.
     */
    public BackgroundJobScheduler(
            BackgroundJobProcessorService processorService, BackgroundJobSchedulerProperties properties) {
        this.processorService = Objects.requireNonNull(processorService, "processorService");
        this.properties = Objects.requireNonNull(properties, "properties");
    }

    /**
     * Runs one scheduled processing cycle.
     */
    @Scheduled(fixedDelayString = "${trail.background-jobs.scheduler.poll-interval-milliseconds:30000}")
    public void runScheduledCycle() {
        if (!properties.enabled()) {
            return;
        }

        var summary = processorService.processOneCycle(properties.leaseOwner());
        if (summary.claimed() > 0) {
            LOGGER.info(
                    "Background job cycle completed claimed={} succeeded={} retryable={} terminal={}",
                    summary.claimed(),
                    summary.succeeded(),
                    summary.retryable(),
                    summary.terminal());
        }
    }
}
