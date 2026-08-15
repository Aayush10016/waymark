package io.github.aayush10016.trail.intelligence.adapter.in.job;

import io.github.aayush10016.trail.backgroundjob.application.port.BackgroundJobHandler;
import io.github.aayush10016.trail.backgroundjob.application.service.BackgroundJobHandlingResult;
import io.github.aayush10016.trail.backgroundjob.domain.model.BackgroundJob;
import io.github.aayush10016.trail.backgroundjob.domain.model.BackgroundJobType;
import io.github.aayush10016.trail.intelligence.application.service.ProblemSessionEnrichmentJobStatus;
import io.github.aayush10016.trail.intelligence.application.service.ProblemSessionEnrichmentService;
import java.util.Objects;

/**
 * Background-job adapter for structured Problem Session enrichment.
 */
public class ProblemSessionEnrichmentJobHandler implements BackgroundJobHandler {

    private final ProblemSessionEnrichmentService enrichmentService;

    /**
     * Creates the job handler.
     */
    public ProblemSessionEnrichmentJobHandler(ProblemSessionEnrichmentService enrichmentService) {
        this.enrichmentService = Objects.requireNonNull(enrichmentService, "enrichmentService");
    }

    @Override
    public BackgroundJobType jobType() {
        return BackgroundJobType.PROBLEM_SESSION_ENRICHMENT;
    }

    @Override
    public BackgroundJobHandlingResult handle(BackgroundJob job) {
        Objects.requireNonNull(job, "job");
        var result = enrichmentService.enrich(job.userId(), job.aggregateId());
        if (result.status() == ProblemSessionEnrichmentJobStatus.SUCCEEDED) {
            return BackgroundJobHandlingResult.succeeded();
        }
        if (result.status() == ProblemSessionEnrichmentJobStatus.RETRYABLE_FAILURE) {
            return BackgroundJobHandlingResult.retryableFailure(result.safeFailureCode());
        }
        return BackgroundJobHandlingResult.terminalFailure(result.safeFailureCode());
    }
}
