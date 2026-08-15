package io.github.aayush10016.trail.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * Verifies the frozen ENG-104 release regression gate.
 */
class ReleaseRegressionSuiteContractTest {

    private static final Path SUITE_DOC =
            Path.of("..", "..", "docs", "quality", "release-regression-suite-v1.md").normalize();
    private static final Path SUITE_SCRIPT =
            Path.of("..", "..", "scripts", "release", "run-critical-regression-suite.ps1").normalize();

    @Test
    void releaseRegressionSuiteDocumentsEveryFrozenGate() throws IOException {
        var content = Files.readString(SUITE_DOC);

        assertThat(content)
                .contains("Clean profile")
                .contains("Upgraded profile")
                .contains("API contract")
                .contains("Migration")
                .contains("Auth")
                .contains("Solve")
                .contains("Recall")
                .contains("Deletion")
                .contains("Release package")
                .contains("ENG-104 freezes the regression suite")
                .contains("does not configure private-beta cohorts");
        assertThat(content).doesNotContain("TODO", "FIXME", "ENG-105");
    }

    @Test
    void releaseRegressionScriptRunsBackendExtensionContractAndImageGates() throws IOException {
        var content = Files.readString(SUITE_SCRIPT);

        assertThat(content)
                .contains(".\\mvnw.cmd verify")
                .contains("corepack pnpm verify")
                .contains("corepack pnpm --filter @trail/extension lint")
                .contains("corepack pnpm --filter @trail/extension typecheck")
                .contains("corepack pnpm --filter @trail/extension test")
                .contains("corepack pnpm --filter @trail/extension validate:manifest")
                .contains("corepack pnpm --filter @trail/extension webstore:package")
                .contains("corepack pnpm --filter @trail/extension webstore:validate")
                .contains("corepack pnpm openapi:check")
                .contains("docker build -f apps/backend/Dockerfile");
        assertThat(content).doesNotContain("TODO", "FIXME");
    }
}
