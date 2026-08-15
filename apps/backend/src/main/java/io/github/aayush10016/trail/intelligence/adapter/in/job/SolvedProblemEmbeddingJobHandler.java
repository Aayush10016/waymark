package io.github.aayush10016.trail.intelligence.adapter.in.job;

import io.github.aayush10016.trail.backgroundjob.application.port.BackgroundJobHandler;
import io.github.aayush10016.trail.backgroundjob.application.service.BackgroundJobHandlingResult;
import io.github.aayush10016.trail.backgroundjob.domain.model.BackgroundJob;
import io.github.aayush10016.trail.backgroundjob.domain.model.BackgroundJobType;
import io.github.aayush10016.trail.intelligence.application.service.SolvedProblemEmbeddingJobStatus;
import io.github.aayush10016.trail.intelligence.application.service.SolvedProblemEmbeddingService;
import java.util.Objects;

/**
 * Background-job adapter for solved Problem Session embedding generation.
 */
public class SolvedProblemEmbeddingJobHandler implements BackgroundJobHandler {

    private final SolvedProblemEmbeddingService embeddingService;

    /**
     * Creates the job handler.
     */
    public SolvedProblemEmbeddingJobHandler(SolvedProblemEmbeddingService embeddingService) {
        this.embeddingService = Objects.requireNonNull(embeddingService, "embeddingService");
    }

    @Override
    public BackgroundJobType jobType() {
        return BackgroundJobType.SOLVED_PROBLEM_SESSION_EMBEDDING;
    }

    @Override
    public BackgroundJobHandlingResult handle(BackgroundJob job) {
        Objects.requireNonNull(job, "job");
        var result = embeddingService.embed(job.userId(), job.aggregateId());
        if (result.status() == SolvedProblemEmbeddingJobStatus.SUCCEEDED) {
            return BackgroundJobHandlingResult.succeeded();
        }
        if (result.status() == SolvedProblemEmbeddingJobStatus.RETRYABLE_FAILURE) {
            return BackgroundJobHandlingResult.retryableFailure(result.safeFailureCode());
        }
        return BackgroundJobHandlingResult.terminalFailure(result.safeFailureCode());
    }
}
