# ADR-0007: PostgreSQL 17.x

- **Status:** Accepted
- **Date:** 2026-07-05
- **Decision owners:** Technical Lead, Data/Platform Owner

## Context

PostgreSQL is canonical for identity, capture events, Problem Sessions, search projections, background jobs, and vectors. Major-version choice affects managed availability, extension compatibility, upgrade runway, and operations.

## Decision

Use PostgreSQL 17, pinning the current security-patched minor (17.10 at decision time) in local/test images and requiring the managed service to remain on supported 17.x minors.

## Alternatives considered

- PostgreSQL 18: current major with longer support, but newer managed-service/extension operational surface.
- PostgreSQL 16: very mature, shorter remaining support.
- Distributed SQL/NoSQL: unnecessary for user-scoped relational transactions.

## Why selected

PostgreSQL 17 is mature, broadly managed, compatible with pgvector, and supported until November 2029. It reduces newest-major adoption risk while preserving a multi-year runway.

## Trade-offs

- One year less support than PostgreSQL 18.
- Future major upgrade is inevitable.
- Single-primary relational architecture has write/region limits.

## Risks

- Provider may lag security minors or pgvector builds.
- Raw-event retention can create vacuum/index pressure.
- Major upgrade requires operational planning.

## One-million-user review

Potential bottleneck through event write volume, maintenance, connections, and single-region primary—not because it is version 17.

## Future migration path

Tune/index/pool, vertically scale, chunk retention, then partition/read-replicate from evidence. Upgrade major using provider blue-green/logical replication or `pg_upgrade`, validated on production-scale fixtures with rollback window.

## Consequences

CI/Testcontainers/local/deployed environments must match PostgreSQL 17 behavior and current minor security baseline.
