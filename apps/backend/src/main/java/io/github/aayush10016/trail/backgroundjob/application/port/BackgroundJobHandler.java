package io.github.aayush10016.trail.backgroundjob.application.port;

import io.github.aayush10016.trail.backgroundjob.application.service.BackgroundJobHandlingResult;
import io.github.aayush10016.trail.backgroundjob.domain.model.BackgroundJob;
import io.github.aayush10016.trail.backgroundjob.domain.model.BackgroundJobType;

/**
 * Executes one claimed durable background job outside the claim transaction.
 */
public interface BackgroundJobHandler {

    /**
     * @return supported durable job type
     */
    BackgroundJobType jobType();

    /**
     * Handles a claimed job.
     */
    BackgroundJobHandlingResult handle(BackgroundJob job);
}
