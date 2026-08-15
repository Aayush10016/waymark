# Render PostgreSQL Compatibility v1

ENG-098 validates the database assumptions required before Trail can be deployed to Render. This document is a deployment-readiness contract, not a production environment configuration.

## Required database capability

- PostgreSQL version: managed PostgreSQL 17.x.
- pgvector version: pgvector 0.8.2 or a provider-approved compatible 0.8.x build after explicit migration review.
- Extension: `vector` must be installed before Flyway migrations depending on vector columns or operators run.
- SQL behavior: local, Testcontainers, and Render environments must support standard PostgreSQL transactions, constraints, JSONB, indexes, and pgvector distance operators used by Trail.

## Required connectivity

- Backend uses Render's internal URL/private database connection for application traffic.
- Public/external database access remains disabled or restricted to explicitly approved break-glass maintenance IPs.
- Database credentials are supplied through Render secrets, not committed files.
- Database access is limited to the backend service; the Chrome extension never connects to PostgreSQL directly.

## Required pooling

- Connection pooling must be enabled either through Render's managed pooler or backend Hikari settings validated for the selected plan.
- Pool size must fit the selected PostgreSQL plan and Spring Boot web/background workload.
- Pooling changes must preserve Flyway migration startup behavior.

## Required backups

- Managed automated backups must be enabled.
- Restore procedure must be exercised before production launch.
- Backup restore must account for account-deletion and retention obligations before restored data becomes user-visible.

## Validation performed

- Local/Testcontainers verification uses the pinned image `pgvector/pgvector:0.8.2-pg17`.
- PostgreSQL reports version `17.x`.
- pgvector reports extension version `0.8.2`.
- Vector distance operator execution succeeds.
- Documentation contract verifies internal URL, pooling, backups, and external restriction requirements are present before ENG-100 environment configuration begins.

## Deferred

ENG-098 does not create Render services, define production environment variables, configure secrets, run a real Render deployment, provision backups, or set provider-specific scaling policy. Those remain ENG-100 and later.
