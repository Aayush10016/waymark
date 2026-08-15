# Trail Architecture Decision Records

Trail v1.1 architecture decisions are frozen here. An accepted ADR may be superseded only by a new ADR that identifies the original, migration plan, owner, and rollout/rollback strategy.

## Status meanings

- **Accepted:** implementation must follow it.
- **Proposed:** decision is reviewed but blocked on named evidence; no dependent implementation may assume an answer.
- **Superseded:** retained for history; replacement ADR is authoritative.

## Index

| ADR | Decision | Status | One-million-user bottleneck? |
|---|---|---|---|
| [0001](ADR-0001-java-version.md) | Java 17 language/runtime baseline | Accepted | No; upgrade for support/performance |
| [0002](ADR-0002-build-system.md) | Maven 3.9.16 with Wrapper | Accepted | No |
| [0003](ADR-0003-spring-boot-version.md) | Spring Boot 4.1.x | Superseded by 0027 | No |
| [0004](ADR-0004-monorepo-structure.md) | One polyglot monorepo | Accepted | Possibly; build graph optimization |
| [0005](ADR-0005-backend-architecture.md) | Modular monolith with Clean Architecture boundaries | Accepted | Possibly; extract measured hotspots |
| [0006](ADR-0006-chrome-manifest-v3.md) | Chrome MV3, side panel, no content scripts | Accepted | No; distribution/platform concentration risk |
| [0007](ADR-0007-postgresql-version.md) | PostgreSQL 17.x | Accepted | Possibly; scale/upgrade operationally |
| [0008](ADR-0008-pgvector-version.md) | pgvector 0.8.2 | Accepted | Possibly; vector workload migration |
| [0009](ADR-0009-authentication-strategy.md) | OIDC PKCE + JWT access + opaque rotating refresh | Superseded by 0028 | No; session store may need scaling |
| [0010](ADR-0010-ai-provider-abstraction.md) | Two narrow provider-neutral AI ports | Accepted | No |
| [0011](ADR-0011-embedding-model-abstraction.md) | Versioned fixed-dimension embedding generations | Accepted | No; migrations are planned |
| [0012](ADR-0012-vector-storage-strategy.md) | pgvector in PostgreSQL, exact search first | Accepted | Yes; dedicated search may be required |
| [0013](ADR-0013-local-cache-strategy.md) | No server cache; query/local caches only | Accepted | Yes; introduce Redis only from evidence |
| [0014](ADR-0014-browser-storage-strategy.md) | IndexedDB + chrome.storage.local by responsibility | Accepted | No; quota/large-history limits |
| [0015](ADR-0015-logging-framework.md) | SLF4J/Logback with structured content-free logs | Accepted | No; transport may change |
| [0016](ADR-0016-testing-frameworks.md) | JUnit/Testcontainers + Vitest/RTL/Playwright | Accepted | No; CI partitioning required |
| [0017](ADR-0017-docker-strategy.md) | OCI backend image; Compose/local containers only | Accepted | No |
| [0018](ADR-0018-deployment-strategy.md) | Generic managed container deployment | Superseded by 0029 | Yes; regional/availability evolution |
| [0019](ADR-0019-ci-cd-strategy.md) | GitHub Actions, build once/promote | Accepted | Possibly; runner/build scaling |
| [0020](ADR-0020-coding-conventions.md) | Automated Java/TypeScript standards | Accepted | No |
| [0021](ADR-0021-package-naming.md) | `com.trail` capability-first packages | Superseded by 0030 | No; root rename is expensive/noisy |
| [0022](ADR-0022-api-versioning.md) | URL major version `/api/v1` + OpenAPI compatibility | Accepted | No |
| [0023](ADR-0023-background-jobs.md) | PostgreSQL-backed explicit jobs | Accepted | Yes; extract queue/workers if needed |
| [0024](ADR-0024-frontend-state.md) | TanStack Query server state; Zustand UI state | Accepted | No |
| [0025](ADR-0025-database-migrations.md) | Flyway forward-only expand/migrate/contract | Accepted | No; operational process matures |
| [0026](ADR-0026-contract-strategy.md) | OpenAPI + generated client + runtime extension schemas | Accepted | No |
| [0027](ADR-0027-spring-boot-3.md) | Spring Boot 3.5.15 | Accepted | No |
| [0028](ADR-0028-email-password-authentication.md) | Email/password + JWT access + rotating refresh | Accepted | No; abuse/session scaling |
| [0029](ADR-0029-render-deployment.md) | Render Docker + managed PostgreSQL | Accepted | Yes; platform/region limits |
| [0030](ADR-0030-package-namespace.md) | `io.github.aayush10016.trail` capability-first packages | Accepted | No |
| [0031](ADR-0031-observability.md) | Actuator/Micrometer/JSON logs only | Accepted | Yes; observability platform later |
| [0032](ADR-0032-feature-flags.md) | Typed configuration-backed global flags | Accepted | Yes; targeting/runtime management later |
| [0033](ADR-0033-explainable-ai-provenance.md) | Structured AI/recommendation provenance | Accepted | No; retention/partition at scale |

## Freeze rule

Implementation PRs cite applicable ADRs. Deviations require a new proposed ADR before code changes. Reversible local implementation details do not need ADRs.
