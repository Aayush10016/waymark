package io.github.aayush10016.trail.backgroundjob.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.aayush10016.trail.backgroundjob.application.port.BackgroundJobHandler;
import io.github.aayush10016.trail.backgroundjob.application.port.BackgroundJobMetrics;
import io.github.aayush10016.trail.backgroundjob.domain.model.BackgroundJob;
import io.github.aayush10016.trail.backgroundjob.domain.model.BackgroundJobAggregateType;
import io.github.aayush10016.trail.backgroundjob.domain.model.BackgroundJobState;
import io.github.aayush10016.trail.backgroundjob.domain.model.BackgroundJobType;
import io.github.aayush10016.trail.backgroundjob.domain.port.BackgroundJobRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BackgroundJobProcessorServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-16T08:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final BackgroundJobProcessingPolicy POLICY =
            new BackgroundJobProcessingPolicy(10, 3, Duration.ofMinutes(5), Duration.ofSeconds(30), Duration.ofMinutes(5));

    @Test
    void skipsClaimingWhenNoRealHandlerIsRegistered() {
        var repository = new InMemoryBackgroundJobRepository(List.of(job(1)));
        var processor = processor(repository, List.of(), new RecordingMetrics());

        var summary = processor.processOneCycle("worker-a");

        assertThat(summary).isEqualTo(BackgroundJobProcessingSummary.empty());
        assertThat(repository.claimCalls).isZero();
    }

    @Test
    void marksSuccessfulJobsTerminalSuccessAndRecordsMetrics() {
        var repository = new InMemoryBackgroundJobRepository(List.of(job(1)));
        var metrics = new RecordingMetrics();
        var processor = processor(repository, List.of(handler(BackgroundJobHandlingResult.succeeded())), metrics);

        var summary = processor.processOneCycle("worker-a");

        assertThat(summary).isEqualTo(new BackgroundJobProcessingSummary(1, 1, 0, 0));
        assertThat(repository.jobs.get(0).state()).isEqualTo(BackgroundJobState.SUCCEEDED);
        assertThat(repository.jobs.get(0).leaseOwner()).isNull();
        assertThat(metrics.succeeded).isEqualTo(1);
    }

    @Test
    void schedulesRetryWithCappedExponentialBackoffBeforeMaxAttempts() {
        var claimedJob = job(1);
        var repository = new InMemoryBackgroundJobRepository(List.of(claimedJob));
        var processor = processor(
                repository,
                List.of(handler(BackgroundJobHandlingResult.retryableFailure("PROVIDER_TIMEOUT"))),
                new RecordingMetrics());

        var summary = processor.processOneCycle("worker-a");

        assertThat(summary).isEqualTo(new BackgroundJobProcessingSummary(1, 0, 1, 0));
        var stored = repository.jobs.get(0);
        assertThat(stored.state()).isEqualTo(BackgroundJobState.FAILED_RETRYABLE);
        assertThat(stored.nextAttemptAt()).isEqualTo(NOW.plusSeconds(30));
        assertThat(repository.lastSafeFailureCode).isEqualTo("PROVIDER_TIMEOUT");
    }

    @Test
    void convertsRetryableFailureToTerminalWhenMaxAttemptsIsReached() {
        var repository = new InMemoryBackgroundJobRepository(List.of(job(3)));
        var metrics = new RecordingMetrics();
        var processor = processor(
                repository,
                List.of(handler(BackgroundJobHandlingResult.retryableFailure("PROVIDER_TIMEOUT"))),
                metrics);

        var summary = processor.processOneCycle("worker-a");

        assertThat(summary).isEqualTo(new BackgroundJobProcessingSummary(1, 0, 0, 1));
        assertThat(repository.jobs.get(0).state()).isEqualTo(BackgroundJobState.FAILED_TERMINAL);
        assertThat(metrics.terminal).isEqualTo(1);
    }

    @Test
    void recordsExplicitTerminalFailureWithoutRetrying() {
        var repository = new InMemoryBackgroundJobRepository(List.of(job(1)));
        var processor = processor(
                repository,
                List.of(handler(BackgroundJobHandlingResult.terminalFailure("INVALID_INPUT"))),
                new RecordingMetrics());

        var summary = processor.processOneCycle("worker-a");

        assertThat(summary).isEqualTo(new BackgroundJobProcessingSummary(1, 0, 0, 1));
        assertThat(repository.jobs.get(0).state()).isEqualTo(BackgroundJobState.FAILED_TERMINAL);
        assertThat(repository.lastSafeFailureCode).isEqualTo("INVALID_INPUT");
    }

    @Test
    void treatsUnhandledHandlerExceptionsAsRetryableSafeFailures() {
        var repository = new InMemoryBackgroundJobRepository(List.of(job(1)));
        var processor = processor(
                repository,
                List.of(new ThrowingHandler()),
                new RecordingMetrics());

        var summary = processor.processOneCycle("worker-a");

        assertThat(summary).isEqualTo(new BackgroundJobProcessingSummary(1, 0, 1, 0));
        assertThat(repository.lastSafeFailureCode).isEqualTo("HANDLER_EXCEPTION");
    }

    @Test
    void rejectsDuplicateHandlersForTheSameJobType() {
        var first = handler(BackgroundJobHandlingResult.succeeded());
        var second = handler(BackgroundJobHandlingResult.succeeded());

        assertThatThrownBy(() -> processor(new InMemoryBackgroundJobRepository(List.of()), List.of(first, second), new RecordingMetrics()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void calculatesCappedBackoff() {
        var policy = new BackgroundJobProcessingPolicy(
                1,
                10,
                Duration.ofMinutes(5),
                Duration.ofSeconds(30),
                Duration.ofMinutes(2));

        assertThat(policy.retryDelayForAttempt(1)).isEqualTo(Duration.ofSeconds(30));
        assertThat(policy.retryDelayForAttempt(2)).isEqualTo(Duration.ofSeconds(60));
        assertThat(policy.retryDelayForAttempt(4)).isEqualTo(Duration.ofMinutes(2));
    }

    private static BackgroundJobProcessorService processor(
            BackgroundJobRepository repository,
            Collection<BackgroundJobHandler> handlers,
            BackgroundJobMetrics metrics) {
        return new BackgroundJobProcessorService(repository, handlers, POLICY, CLOCK, metrics);
    }

    private static BackgroundJobHandler handler(BackgroundJobHandlingResult result) {
        return new StaticHandler(result);
    }

    private static BackgroundJob job(int attempts) {
        return new BackgroundJob(
                UUID.randomUUID(),
                UUID.randomUUID(),
                BackgroundJobAggregateType.PROBLEM_SESSION,
                UUID.randomUUID(),
                BackgroundJobType.PROBLEM_SESSION_ENRICHMENT,
                BackgroundJobState.READY,
                attempts,
                NOW.minusSeconds(30),
                null,
                null,
                "{}",
                NOW.minusSeconds(60),
                NOW.minusSeconds(60));
    }

    private static final class StaticHandler implements BackgroundJobHandler {

        private final BackgroundJobHandlingResult result;

        private StaticHandler(BackgroundJobHandlingResult result) {
            this.result = result;
        }

        @Override
        public BackgroundJobType jobType() {
            return BackgroundJobType.PROBLEM_SESSION_ENRICHMENT;
        }

        @Override
        public BackgroundJobHandlingResult handle(BackgroundJob job) {
            return result;
        }
    }

    private static final class ThrowingHandler implements BackgroundJobHandler {

        @Override
        public BackgroundJobType jobType() {
            return BackgroundJobType.PROBLEM_SESSION_ENRICHMENT;
        }

        @Override
        public BackgroundJobHandlingResult handle(BackgroundJob job) {
            throw new IllegalStateException("provider failure with no sensitive data");
        }
    }

    private static final class RecordingMetrics implements BackgroundJobMetrics {

        private int retryable;
        private int succeeded;
        private int terminal;

        @Override
        public void recordSucceeded(BackgroundJobType jobType) {
            succeeded += 1;
        }

        @Override
        public void recordRetryableFailure(BackgroundJobType jobType) {
            retryable += 1;
        }

        @Override
        public void recordTerminalFailure(BackgroundJobType jobType) {
            terminal += 1;
        }
    }

    private static final class InMemoryBackgroundJobRepository implements BackgroundJobRepository {

        private final List<BackgroundJob> jobs = new ArrayList<>();
        private int claimCalls;
        private String lastSafeFailureCode;

        private InMemoryBackgroundJobRepository(Collection<BackgroundJob> jobs) {
            this.jobs.addAll(jobs);
        }

        @Override
        public boolean insertIfAbsent(BackgroundJob job) {
            jobs.add(job);
            return true;
        }

        @Override
        public List<BackgroundJob> claimReadyJobs(
                BackgroundJobType jobType, String leaseOwner, Instant now, Instant leaseExpiresAt, int limit) {
            claimCalls += 1;
            var claimed = jobs.stream()
                    .filter(job -> job.jobType() == jobType)
                    .filter(job -> job.state() == BackgroundJobState.READY || job.state() == BackgroundJobState.FAILED_RETRYABLE)
                    .filter(job -> !job.nextAttemptAt().isAfter(now))
                    .limit(limit)
                    .map(job -> leased(job, leaseOwner, leaseExpiresAt))
                    .toList();
            for (var claimedJob : claimed) {
                replace(claimedJob);
            }
            return claimed;
        }

        @Override
        public boolean markSucceeded(UUID jobId, String leaseOwner, Instant completedAt) {
            return complete(jobId, leaseOwner, BackgroundJobState.SUCCEEDED, null, completedAt);
        }

        @Override
        public boolean markRetryableFailure(
                UUID jobId, String leaseOwner, String safeFailureCode, Instant nextAttemptAt, Instant now) {
            lastSafeFailureCode = safeFailureCode;
            return transitionLeased(jobId, leaseOwner, job -> new BackgroundJob(
                    job.id(),
                    job.userId(),
                    job.aggregateType(),
                    job.aggregateId(),
                    job.jobType(),
                    BackgroundJobState.FAILED_RETRYABLE,
                    job.attempts(),
                    nextAttemptAt,
                    null,
                    null,
                    job.payloadJson(),
                    job.createdAt(),
                    now));
        }

        @Override
        public boolean markTerminalFailure(UUID jobId, String leaseOwner, String safeFailureCode, Instant completedAt) {
            lastSafeFailureCode = safeFailureCode;
            return complete(jobId, leaseOwner, BackgroundJobState.FAILED_TERMINAL, safeFailureCode, completedAt);
        }

        private boolean complete(
                UUID jobId,
                String leaseOwner,
                BackgroundJobState state,
                String safeFailureCode,
                Instant completedAt) {
            lastSafeFailureCode = safeFailureCode;
            return transitionLeased(jobId, leaseOwner, job -> new BackgroundJob(
                    job.id(),
                    job.userId(),
                    job.aggregateType(),
                    job.aggregateId(),
                    job.jobType(),
                    state,
                    job.attempts(),
                    job.nextAttemptAt(),
                    null,
                    null,
                    job.payloadJson(),
                    job.createdAt(),
                    completedAt));
        }

        private boolean transitionLeased(UUID jobId, String leaseOwner, java.util.function.Function<BackgroundJob, BackgroundJob> transition) {
            for (var index = 0; index < jobs.size(); index += 1) {
                var job = jobs.get(index);
                if (job.id().equals(jobId) && leaseOwner.equals(job.leaseOwner())) {
                    jobs.set(index, transition.apply(job));
                    return true;
                }
            }
            return false;
        }

        private void replace(BackgroundJob replacement) {
            for (var index = 0; index < jobs.size(); index += 1) {
                if (jobs.get(index).id().equals(replacement.id())) {
                    jobs.set(index, replacement);
                    return;
                }
            }
        }

        private static BackgroundJob leased(BackgroundJob job, String leaseOwner, Instant leaseExpiresAt) {
            return new BackgroundJob(
                    job.id(),
                    job.userId(),
                    job.aggregateType(),
                    job.aggregateId(),
                    job.jobType(),
                    BackgroundJobState.LEASED,
                    job.attempts(),
                    job.nextAttemptAt(),
                    leaseOwner,
                    leaseExpiresAt,
                    job.payloadJson(),
                    job.createdAt(),
                    NOW);
        }
    }
}
