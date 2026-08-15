package io.github.aayush10016.trail.problem.config;

import io.github.aayush10016.trail.problem.application.service.ManualProblemSessionService;
import io.github.aayush10016.trail.problem.application.service.ProblemSessionDeletionService;
import io.github.aayush10016.trail.problem.application.service.ProblemSessionDetailQueryService;
import io.github.aayush10016.trail.problem.application.service.ProblemSessionSolutionService;
import io.github.aayush10016.trail.backgroundjob.domain.port.BackgroundJobRepository;
import io.github.aayush10016.trail.problem.domain.port.ProblemSessionDeletionRepository;
import io.github.aayush10016.trail.problem.domain.port.ProblemSessionRepository;
import io.github.aayush10016.trail.problem.domain.port.SessionArtifactRepository;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Problem Session capability wiring.
 */
@Configuration
public class ProblemConfiguration {

    /**
     * Creates the manual Problem Session command service.
     */
    @Bean
    public ManualProblemSessionService manualProblemSessionService(
            ProblemSessionRepository problemSessionRepository, Clock clock) {
        return new ManualProblemSessionService(problemSessionRepository, clock);
    }

    /**
     * Creates the Problem Session detail query service.
     */
    @Bean
    public ProblemSessionDetailQueryService problemSessionDetailQueryService(
            ProblemSessionRepository problemSessionRepository, SessionArtifactRepository sessionArtifactRepository) {
        return new ProblemSessionDetailQueryService(problemSessionRepository, sessionArtifactRepository);
    }

    /**
     * Creates the Problem Session deletion service.
     */
    @Bean
    public ProblemSessionDeletionService problemSessionDeletionService(
            ProblemSessionDeletionRepository problemSessionDeletionRepository, Clock clock) {
        return new ProblemSessionDeletionService(problemSessionDeletionRepository, clock);
    }

    /**
     * Creates the Problem Session final-solution command service.
     */
    @Bean
    public ProblemSessionSolutionService problemSessionSolutionService(
            ProblemSessionRepository problemSessionRepository,
            SessionArtifactRepository sessionArtifactRepository,
            BackgroundJobRepository backgroundJobRepository,
            Clock clock) {
        return new ProblemSessionSolutionService(
                problemSessionRepository, sessionArtifactRepository, backgroundJobRepository, clock);
    }
}
