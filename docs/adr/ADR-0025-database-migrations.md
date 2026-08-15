# ADR-0025: Flyway Forward-Only Database Migrations

- **Status:** Accepted
- **Date:** 2026-07-05
- **Decision owners:** Data/Platform Owner, Backend Owner

## Context

Trail needs reviewable schema evolution across local/test/staging/production while preserving rolling compatibility, retention, and vector model migrations.

## Decision

Use Flyway versioned forward-only migrations. Applied migrations are immutable. Breaking changes use expand/backfill/contract across releases; large backfills are resumable application jobs, not blocking migration SQL. CI migrates from empty and previous-release snapshots.

## Alternatives considered

- Liquibase: richer changelog/rollback formats, more abstraction than needed.
- Hibernate auto-DDL: convenient, unsafe/unreviewable in production.
- Hand-run SQL: flexible, poor audit/reproducibility.
- Down migrations: reassuring but often unsafe for data/destructive changes.

## Why selected

Flyway matches SQL-first PostgreSQL control and creates a simple ordered deployment contract.

## Trade-offs

- Rollback is usually deploy-forward or restore, not automatic down SQL.
- Multi-release changes require discipline.
- Concurrent index/backfill operations need operational handling.

## Risks

- Long locks/table rewrites.
- Migration/app compatibility mismatch.
- Changed historical files/checksum failure.

## One-million-user review

Not a bottleneck, but migration duration/risk grows with data volume.

## Future migration path

Add schema-change lints, production-size rehearsal, online index tools/process, migration windows, and dedicated data-migration jobs. Flyway remains the schema ledger.

## Consequences

Every migration includes lock/compatibility/retention/rollback-by-deploy review. Production auto-DDL is disabled.
