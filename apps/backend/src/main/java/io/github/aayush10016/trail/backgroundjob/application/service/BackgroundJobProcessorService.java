package io.github.aayush10016.trail.backgroundjob.application.service;

import io.github.aayush10016.trail.backgroundjob.application.port.BackgroundJobHandler;
import io.github.aayush10016.trail.backgroundjob.application.port.BackgroundJobMetrics;
import io.github.aayush10016.trail.backgroundjob.domain.model.BackgroundJob;
import io.github.aayush10016.trail.backgroundjob.domain.model.BackgroundJobType;
import io.github.aayush10016.trail.backgroundjob.domain.port.BackgroundJobRepository;
import java.time.Clock;
import java.util.Collection;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Claims durable background jobs, invokes registered handlers outside claim persistence, and records terminal state.
 */
public class BackgroundJobProcessorService {

    private static final Logger LOGGER = LoggerFactory.getLogger(BackgroundJobProcessorService.class);
    private static final String UNHANDLED_EXCEPTION_FAILURE_CODE = "HANDLER_EXCEPTION";

    private final Clock clock;
    private final Map<BackgroundJobType, BackgroundJobHandler> handlers;
    private final BackgroundJobMetrics metrics;
    private final BackgroundJobProcessingPolicy policy;
    private final BackgroundJobRepository repository;

    /**
     * Creates the processor.
     */
    public BackgroundJobProcessorService(
            BackgroundJobRepository repository,
            Collection<BackgroundJobHandler> handlers,
            BackgroundJobProcessingPolicy policy,
            Clock clock,
            BackgroundJobMetrics metrics) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.policy = Objects.requireNonNull(policy, "policy");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.metrics = Objects.requireNonNull(metrics, "metrics");
        this.handlers = handlerMap(handlers);
    }

    /**
     * Processes one bounded scheduler cycle for all registered job handlers.
     */
    public BackgroundJobProcessingSummary processOneCycle(String leaseOwner) {
        if (leaseOwner == null || leaseOwner.isBlank()) {
            throw new IllegalArgumentException("leaseOwner must not be blank");
        }
        if (handlers.isEmpty()) {
            return BackgroundJobProcessingSummary.empty();
        }

        var claimed = 0;
        var succeeded = 0;
        var retryable = 0;
        var terminal = 0;
        var sanitizedLeaseOwner = leaseOwner.strip();

        for (var entry : handlers.entrySet()) {
            var now = clock.instant();
            var jobs = repository.claimReadyJobs(
                    entry.getKey(),
                    sanitizedLeaseOwner,
                    now,
                    now.plus(policy.leaseDuration()),
                    policy.batchSize());
            claimed += jobs.size();

            for (var job : jobs) {
                var result = handleJob(entry.getValue(), job);
                switch (result.status()) {
                    case SUCCEEDED -> {
                        if (repository.markSucceeded(job.id(), sanitizedLeaseOwner, clock.instant())) {
                            metrics.recordSucceeded(job.jobType());
                            succeeded += 1;
                        }
                    }
                    case RETRYABLE_FAILURE -> {
                        if (job.attempts() >= policy.maxAttempts()) {
                            if (repository.markTerminalFailure(
                                    job.id(), sanitizedLeaseOwner, result.safeFailureCode(), clock.instant())) {
                                metrics.recordTerminalFailure(job.jobType());
                                terminal += 1;
                            }
                        } else {
                            var retryAt = clock.instant().plus(policy.retryDelayForAttempt(job.attempts()));
                            if (repository.markRetryableFailure(
                                    job.id(), sanitizedLeaseOwner, result.safeFailureCode(), retryAt, clock.instant())) {
                                metrics.recordRetryableFailure(job.jobType());
                                retryable += 1;
                            }
                        }
                    }
                    case TERMINAL_FAILURE -> {
                        if (repository.markTerminalFailure(
                                job.id(), sanitizedLeaseOwner, result.safeFailureCode(), clock.instant())) {
                            metrics.recordTerminalFailure(job.jobType());
                            terminal += 1;
                        }
                    }
                }
            }
        }

        return new BackgroundJobProcessingSummary(claimed, succeeded, retryable, terminal);
    }

    private BackgroundJobHandlingResult handleJob(BackgroundJobHandler handler, BackgroundJob job) {
        try {
            return handler.handle(job);
        } catch (RuntimeException exception) {
            LOGGER.warn("Background job handler failed jobType={} jobId={}", job.jobType(), job.id());
            return BackgroundJobHandlingResult.retryableFailure(UNHANDLED_EXCEPTION_FAILURE_CODE);
        }
    }

    private static Map<BackgroundJobType, BackgroundJobHandler> handlerMap(Collection<BackgroundJobHandler> handlers) {
        Objects.requireNonNull(handlers, "handlers");
        var mapped = new EnumMap<BackgroundJobType, BackgroundJobHandler>(BackgroundJobType.class);
        for (var handler : handlers) {
            Objects.requireNonNull(handler, "handler");
            var previous = mapped.putIfAbsent(handler.jobType(), handler);
            if (previous != null) {
                throw new IllegalArgumentException("duplicate background job handler for " + handler.jobType());
            }
        }
        return Map.copyOf(mapped);
    }
}
