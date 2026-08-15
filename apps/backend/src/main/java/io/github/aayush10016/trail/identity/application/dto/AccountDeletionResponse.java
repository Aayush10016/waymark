package io.github.aayush10016.trail.identity.application.dto;

import java.time.Instant;

/**
 * Content-safe account deletion result.
 *
 * @param status deletion status
 * @param deletedAt deletion timestamp
 * @param backupDisclosure backup retention disclosure
 * @param localCleanupRequired whether clients must clear local device state
 */
public record AccountDeletionResponse(
        String status, Instant deletedAt, String backupDisclosure, boolean localCleanupRequired) {

    /**
     * Creates a completed account deletion response.
     */
    public static AccountDeletionResponse completed(Instant deletedAt) {
        return new AccountDeletionResponse(
                "COMPLETED",
                deletedAt,
                "Encrypted backups age out under the published retention window.",
                true);
    }
}
