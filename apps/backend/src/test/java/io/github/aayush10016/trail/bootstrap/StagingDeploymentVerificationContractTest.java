package io.github.aayush10016.trail.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * Verifies the ENG-101 staging verification contract without claiming live Render deployment.
 */
class StagingDeploymentVerificationContractTest {

    private static final Path VERIFICATION_DOC =
            Path.of("..", "..", "docs", "deployment", "staging-deployment-verification-v1.md").normalize();
    private static final Path VERIFICATION_SCRIPT =
            Path.of("..", "..", "scripts", "release", "verify-render-staging.ps1").normalize();

    @Test
    void stagingVerificationDocumentsRequiredReleaseEvidence() throws IOException {
        var content = Files.readString(VERIFICATION_DOC);

        assertThat(content)
                .contains("Flyway migration")
                .contains("/actuator/health/readiness")
                .contains("/actuator/health/liveness")
                .contains("Private metrics")
                .contains("Internal DB")
                .contains("Log redaction")
                .contains("This substitute does not claim that staging was deployed.");
        assertThat(content).doesNotContain("TODO", "FIXME");
    }

    @Test
    void stagingVerificationScriptChecksReadinessLivenessAndMetricsPrivacy() throws IOException {
        var content = Files.readString(VERIFICATION_SCRIPT);

        assertThat(content)
                .contains("/actuator/health/readiness")
                .contains("/actuator/health/liveness")
                .contains("/actuator/metrics")
                .contains("Public metrics endpoint must not be exposed.");
        assertThat(content).doesNotContain("TODO", "FIXME");
    }
}
