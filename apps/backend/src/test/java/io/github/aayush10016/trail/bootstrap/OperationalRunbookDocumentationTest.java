package io.github.aayush10016.trail.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Verifies that release-blocking operational runbooks keep the required executable sections.
 */
class OperationalRunbookDocumentationTest {

    private static final Path RUNBOOK_ROOT = Path.of("..", "..", "docs", "runbooks").normalize();
    private static final List<String> REQUIRED_SECTIONS = List.of(
            "## Owners",
            "## Detection",
            "## Mitigation",
            "## Recovery",
            "## Verification"
    );
    private static final List<String> RUNBOOKS = List.of(
            "incident-response.md",
            "deletion-incident.md",
            "provider-outage.md",
            "auth-incident.md",
            "tabletop-exercise-v1.md"
    );

    @Test
    void runbooksContainRequiredOperationalSections() throws IOException {
        for (var runbook : RUNBOOKS) {
            var content = Files.readString(RUNBOOK_ROOT.resolve(runbook));

            assertThat(content).contains(REQUIRED_SECTIONS);
            assertThat(content).doesNotContain("TODO", "FIXME");
        }
    }

    @Test
    void tabletopFindingsAreResolved() throws IOException {
        var content = Files.readString(RUNBOOK_ROOT.resolve("tabletop-exercise-v1.md"));

        assertThat(content).contains("## Findings resolved");
        assertThat(content).contains("Tabletop findings are resolved");
    }
}
