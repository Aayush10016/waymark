package io.github.aayush10016.trail.problem.application.command;

import java.time.Instant;
import java.util.UUID;

/**
 * Command to manually start a Problem Session.
 *
 * @param problemSessionId optional client-generated ID for idempotent starts
 * @param title optional bounded title
 * @param startedAt explicit start timestamp
 */
public record StartProblemSessionCommand(UUID problemSessionId, String title, Instant startedAt) {}
