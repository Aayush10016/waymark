# Render Backup, Restore, and Rollback Exercise v1

ENG-102 defines the required Render backup/restore and backend image rollback exercise.

## Required measurements

| Measurement | Required evidence |
|---|---|
| RPO | Timestamp of latest restorable managed backup before restore. |
| RTO | Time from restore start to backend readiness on restored database. |
| Restore integrity | Flyway version, user-owned row counts, constraints, pgvector extension, and representative API smoke checks. |
| Image rollback | Previous immutable backend image starts and passes readiness against the compatible database state. |
| Migration compatibility | Forward-only migrations are compatible with rollback-by-image for the approved rollback window. |

## Restore procedure

1. Record current backend image digest, Flyway schema version, PostgreSQL version, pgvector version, and backup timestamp.
2. Restore the selected Render managed PostgreSQL backup into an isolated staging database.
3. Point a staging backend instance at the restored internal database URL.
4. Verify `/actuator/health/readiness` and `/actuator/health/liveness`.
5. Verify Flyway reports the expected schema version and no pending destructive migration is required.
6. Verify privacy-sensitive deletion records are reapplied or respected before restored data becomes user-visible.
7. Record measured RPO and RTO.

## Rollback procedure

1. Select the previously promoted immutable backend image.
2. Deploy it to staging without changing database state.
3. Confirm readiness and liveness.
4. Exercise authentication, problem-session solve, Recall, and deletion smoke paths where test accounts exist.
5. If rollback fails due migration incompatibility, stop promotion and create a release-blocking migration compatibility issue.

## Repository-side validation

When Render credentials are unavailable, local validation is limited to Testcontainers migration integrity, Docker image readiness, and documentation contract checks. This does not claim a live Render backup restore or rollback occurred.

## Deferred

ENG-102 does not configure private-beta cohorts, execute production rollback, publish the extension, or sign release readiness.
