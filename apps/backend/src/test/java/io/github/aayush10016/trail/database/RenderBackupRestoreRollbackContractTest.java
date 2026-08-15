package io.github.aayush10016.trail.database;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * Verifies the ENG-102 backup/restore/rollback exercise contract.
 */
class RenderBackupRestoreRollbackContractTest {

    private static final Path CONTRACT =
            Path.of("..", "..", "docs", "deployment", "render-backup-restore-rollback-v1.md").normalize();

    @Test
    void backupRestoreRollbackContractDocumentsMeasurementsAndSafetyChecks() throws IOException {
        var content = Files.readString(CONTRACT);

        assertThat(content)
                .contains("RPO")
                .contains("RTO")
                .contains("Restore integrity")
                .contains("Image rollback")
                .contains("Migration compatibility")
                .contains("PostgreSQL version")
                .contains("pgvector version")
                .contains("Flyway schema version")
                .contains("rollback-by-image")
                .contains("This does not claim a live Render backup restore or rollback occurred.");
        assertThat(content).doesNotContain("TODO", "FIXME");
    }
}
