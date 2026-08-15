package io.github.aayush10016.trail.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * Verifies the frozen ENG-105 private-beta cohort and support controls.
 */
class PrivateBetaSupportControlsContractTest {

    private static final Path PRIVATE_BETA_DOC =
            Path.of("..", "..", "docs", "release", "private-beta-cohort-support-v1.md").normalize();

    @Test
    void privateBetaControlsCoverFrozenReleaseRequirements() throws IOException {
        var content = Files.readString(PRIVATE_BETA_DOC);

        assertThat(content)
                .contains("ENG-105")
                .contains("Consent and revoke controls")
                .contains("Content-hidden support")
                .contains("Opt-in evaluation")
                .contains("Security contact")
                .contains("Active owner roster")
                .contains("Release owner")
                .contains("Support owner")
                .contains("Security owner")
                .contains("Deletion owner")
                .contains("Platform owner");
    }

    @Test
    void privateBetaSupportDoesNotRequestSensitiveCaptureDataByDefault() throws IOException {
        var content = Files.readString(PRIVATE_BETA_DOC);

        assertThat(content)
                .contains("Support must not request by default")
                .contains("Full browser history")
                .contains("Raw URLs with query strings")
                .contains("Page content")
                .contains("form values")
                .contains("cookies")
                .contains("headers")
                .contains("screenshots")
                .contains("clipboard data")
                .contains("tokens")
                .contains("API keys")
                .contains("passwords")
                .contains("AI provider raw request or response payloads");
    }

    @Test
    void privateBetaDocumentSeparatesRepositoryControlsFromExternalActivation() throws IOException {
        var content = Files.readString(PRIVATE_BETA_DOC);

        assertThat(content)
                .contains("does not deploy Render")
                .contains("publish the Chrome extension")
                .contains("Create the invite-only cohort roster outside the repository")
                .contains("Publish the private-beta support channel")
                .contains("Publish the private-beta security contact")
                .contains("ENG-106 consumes these controls");
        assertThat(content).doesNotContain("TODO", "FIXME");
    }
}
