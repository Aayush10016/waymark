package io.github.aayush10016.trail.intelligence.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.aayush10016.trail.intelligence.adapter.in.job.ProblemSessionEnrichmentJobHandler;
import io.github.aayush10016.trail.intelligence.application.service.ProblemSessionEnrichmentPolicy;
import io.github.aayush10016.trail.intelligence.application.service.ProblemSessionEnrichmentService;
import io.github.aayush10016.trail.intelligence.domain.port.AIDerivationRepository;
import io.github.aayush10016.trail.intelligence.domain.port.AIProvider;
import io.github.aayush10016.trail.intelligence.domain.port.ProblemSessionEnrichmentContextRepository;
import io.github.aayush10016.trail.intelligence.domain.service.AIRedactionPolicy;
import io.github.aayush10016.trail.intelligence.domain.service.PromptRegistry;
import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires structured Problem Session enrichment.
 */
@Configuration
@EnableConfigurationProperties(ProblemSessionEnrichmentProperties.class)
public class ProblemSessionEnrichmentConfiguration {

    /**
     * Creates deterministic AI redaction policy.
     */
    @Bean
    public AIRedactionPolicy aiRedactionPolicy() {
        return new AIRedactionPolicy();
    }

    /**
     * Creates provider-independent enrichment policy.
     */
    @Bean
    public ProblemSessionEnrichmentPolicy problemSessionEnrichmentPolicy(
            ProblemSessionEnrichmentProperties properties) {
        return new ProblemSessionEnrichmentPolicy(properties.model(), properties.timeout());
    }

    /**
     * Creates the enrichment application service.
     */
    @Bean
    public ProblemSessionEnrichmentService problemSessionEnrichmentService(
            ProblemSessionEnrichmentContextRepository contextRepository,
            AIDerivationRepository derivationRepository,
            AIProvider aiProvider,
            PromptRegistry promptRegistry,
            AIRedactionPolicy redactionPolicy,
            ProblemSessionEnrichmentPolicy policy,
            ObjectMapper objectMapper,
            Clock clock) {
        return new ProblemSessionEnrichmentService(
                contextRepository,
                derivationRepository,
                aiProvider,
                promptRegistry,
                redactionPolicy,
                policy,
                objectMapper,
                clock);
    }

    /**
     * Creates the enrichment background job handler.
     */
    @Bean
    public ProblemSessionEnrichmentJobHandler problemSessionEnrichmentJobHandler(
            ProblemSessionEnrichmentService enrichmentService) {
        return new ProblemSessionEnrichmentJobHandler(enrichmentService);
    }
}
