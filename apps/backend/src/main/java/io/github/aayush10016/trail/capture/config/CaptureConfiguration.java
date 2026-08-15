package io.github.aayush10016.trail.capture.config;

import io.github.aayush10016.trail.capture.application.service.CaptureBatchIngestionService;
import io.github.aayush10016.trail.capture.application.service.CaptureConsentDecisionService;
import io.github.aayush10016.trail.capture.application.service.CaptureIngestionValidator;
import io.github.aayush10016.trail.capture.application.service.CaptureStateSummaryService;
import io.github.aayush10016.trail.capture.application.service.RawCaptureRetentionService;
import io.github.aayush10016.trail.capture.domain.model.CaptureSanitizationPolicy;
import io.github.aayush10016.trail.capture.domain.model.CaptureSearchAllowlistEntry;
import io.github.aayush10016.trail.capture.domain.port.CaptureConsentRepository;
import io.github.aayush10016.trail.capture.domain.port.CaptureEventRepository;
import io.github.aayush10016.trail.capture.domain.port.CaptureRetentionRepository;
import io.github.aayush10016.trail.capture.domain.port.CaptureStateRepository;
import io.github.aayush10016.trail.capture.domain.service.UrlQuerySanitizer;
import io.github.aayush10016.trail.problem.domain.port.ProblemSessionRepository;
import io.github.aayush10016.trail.shared.featureflag.FeatureFlagService;
import java.time.Clock;
import java.util.List;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Capture capability wiring.
 */
@Configuration
@EnableConfigurationProperties(CaptureRetentionProperties.class)
public class CaptureConfiguration {

    /**
     * Creates the system UTC clock.
     */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    /**
     * Creates the backend capture ingestion validator.
     */
    @Bean
    public CaptureIngestionValidator captureIngestionValidator() {
        return new CaptureIngestionValidator(new UrlQuerySanitizer(defaultSanitizationPolicy()));
    }

    /**
     * Creates the capture batch ingestion service.
     */
    @Bean
    public CaptureBatchIngestionService captureBatchIngestionService(
            CaptureConsentRepository consentRepository,
            CaptureEventRepository eventRepository,
            ProblemSessionRepository problemSessionRepository,
            CaptureIngestionValidator validator) {
        return new CaptureBatchIngestionService(consentRepository, eventRepository, problemSessionRepository, validator);
    }

    /**
     * Creates the capture consent decision service.
     */
    @Bean
    public CaptureConsentDecisionService captureConsentDecisionService(
            CaptureConsentRepository consentRepository, Clock clock) {
        return new CaptureConsentDecisionService(consentRepository, clock);
    }

    /**
     * Creates the backend capture-state summary service.
     */
    @Bean
    public CaptureStateSummaryService captureStateSummaryService(
            CaptureConsentRepository consentRepository,
            CaptureStateRepository captureStateRepository,
            FeatureFlagService featureFlagService) {
        return new CaptureStateSummaryService(consentRepository, captureStateRepository, featureFlagService);
    }

    /**
     * Creates the raw capture retention service.
     */
    @Bean
    public RawCaptureRetentionService rawCaptureRetentionService(
            CaptureRetentionRepository retentionRepository, Clock clock) {
        return new RawCaptureRetentionService(retentionRepository, clock);
    }

    private CaptureSanitizationPolicy defaultSanitizationPolicy() {
        return new CaptureSanitizationPolicy(
                "url-query-sanitation-v1",
                List.of(
                        new CaptureSearchAllowlistEntry(
                                "google", List.of("google.com", "www.google.com"), List.of("/search"), List.of("q")),
                        new CaptureSearchAllowlistEntry(
                                "bing", List.of("bing.com", "www.bing.com"), List.of("/search"), List.of("q")),
                        new CaptureSearchAllowlistEntry(
                                "duckduckgo", List.of("duckduckgo.com", "www.duckduckgo.com"), List.of("/"), List.of("q")),
                        new CaptureSearchAllowlistEntry(
                                "stackoverflow", List.of("stackoverflow.com"), List.of("/search"), List.of("q")),
                        new CaptureSearchAllowlistEntry(
                                "github", List.of("github.com"), List.of("/search"), List.of("q"))));
    }
}
