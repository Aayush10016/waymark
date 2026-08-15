package io.github.aayush10016.trail.intelligence.config;

import io.github.aayush10016.trail.intelligence.adapter.in.job.SolvedProblemEmbeddingJobHandler;
import io.github.aayush10016.trail.intelligence.application.service.SolvedProblemEmbeddingPolicy;
import io.github.aayush10016.trail.intelligence.application.service.SolvedProblemEmbeddingService;
import io.github.aayush10016.trail.intelligence.domain.port.EmbeddingProvider;
import io.github.aayush10016.trail.intelligence.domain.port.ProblemEmbeddingRepository;
import io.github.aayush10016.trail.intelligence.domain.port.SolvedProblemEmbeddingContextRepository;
import io.github.aayush10016.trail.intelligence.domain.service.AIRedactionPolicy;
import io.github.aayush10016.trail.intelligence.domain.service.CanonicalEmbeddingDocumentBuilder;
import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires solved Problem Session embedding jobs.
 */
@Configuration
@EnableConfigurationProperties(SolvedProblemEmbeddingProperties.class)
public class SolvedProblemEmbeddingConfiguration {

    /**
     * Creates the canonical embedding document builder.
     */
    @Bean
    public CanonicalEmbeddingDocumentBuilder canonicalEmbeddingDocumentBuilder() {
        return new CanonicalEmbeddingDocumentBuilder();
    }

    /**
     * Creates solved-session embedding policy.
     */
    @Bean
    public SolvedProblemEmbeddingPolicy solvedProblemEmbeddingPolicy(SolvedProblemEmbeddingProperties properties) {
        return new SolvedProblemEmbeddingPolicy(properties.generationKey(), properties.timeout());
    }

    /**
     * Creates solved-session embedding application service.
     */
    @Bean
    public SolvedProblemEmbeddingService solvedProblemEmbeddingService(
            SolvedProblemEmbeddingContextRepository contextRepository,
            ProblemEmbeddingRepository embeddingRepository,
            EmbeddingProvider embeddingProvider,
            AIRedactionPolicy redactionPolicy,
            CanonicalEmbeddingDocumentBuilder documentBuilder,
            SolvedProblemEmbeddingPolicy policy,
            Clock clock) {
        return new SolvedProblemEmbeddingService(
                contextRepository,
                embeddingRepository,
                embeddingProvider,
                redactionPolicy,
                documentBuilder,
                policy,
                clock);
    }

    /**
     * Creates solved-session embedding background job handler.
     */
    @Bean
    public SolvedProblemEmbeddingJobHandler solvedProblemEmbeddingJobHandler(
            SolvedProblemEmbeddingService embeddingService) {
        return new SolvedProblemEmbeddingJobHandler(embeddingService);
    }
}
