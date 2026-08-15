package io.github.aayush10016.trail.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * Verifies the frozen ENG-106 release-readiness review contract.
 */
class ReleaseReadinessReviewContractTest {

    private static final Path RELEASE_READINESS_DOC =
            Path.of("..", "..", "docs", "release", "v1.1-release-readiness-review.md").normalize();

    @Test
    void releaseReadinessReviewDefinesP0PolicyAndSignedGates() throws IOException {
        var content = Files.readString(RELEASE_READINESS_DOC);

        assertThat(content)
                .contains("ENG-106")
                .contains("P0 blocker policy")
                .contains("Signed release gates")
                .contains("Security")
                .contains("Performance")
                .contains("UX")
                .contains("Restore")
                .contains("Disclosure")
                .contains("Security owner")
                .contains("Platform owner")
                .contains("Product owner")
                .contains("Release owner");
    }

    @Test
    void releaseReadinessReviewRequiresCompleteLocalValidation() throws IOException {
        var content = Files.readString(RELEASE_READINESS_DOC);

        assertThat(content)
                .contains("ENG-104 critical regression suite passes")
                .contains("Full Maven verification passes")
                .contains("Full pnpm verification passes")
                .contains("Extension lint, typecheck, tests, and manifest validation pass")
                .contains("OpenAPI client check passes")
                .contains("Web Store package generation and validation pass")
                .contains("Backend Docker image builds")
                .contains("No locally fixable P0 blocker remains");
    }

    @Test
    void releaseReadinessReviewDefinesSeventyTwoHourMonitoringPlan() throws IOException {
        var content = Files.readString(RELEASE_READINESS_DOC);

        assertThat(content)
                .contains("72-hour monitoring plan")
                .contains("Hour 0")
                .contains("Hours 1 through 6")
                .contains("Hours 6 through 24")
                .contains("Hours 24 through 72")
                .contains("Hour 72")
                .contains("Rollback triggers")
                .contains("capture without consent")
                .contains("cross-user data exposure")
                .contains("deletion failure")
                .contains("secret exposure");
    }

    @Test
    void releaseReadinessReviewDoesNotClaimUnavailableExternalActions() throws IOException {
        var content = Files.readString(RELEASE_READINESS_DOC);

        assertThat(content)
                .contains("External signatures are maintained in the private release checklist")
                .contains("Render staging deployment verification")
                .contains("Live Render backup/restore exercise")
                .contains("Chrome Web Store submission")
                .contains("does not create new product capabilities")
                .contains("does not claim external signatures");
        assertThat(content).doesNotContain("TODO", "FIXME");
    }
}
