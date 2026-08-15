# ADR-0002: Maven Build System

- **Status:** Accepted
- **Date:** 2026-07-05
- **Decision owners:** Technical Lead

## Context

The backend needs reproducible dependency management, Spring Boot packaging, tests, Flyway integration, and conventional Java onboarding.

## Decision

Use Apache Maven 3.9.16 through Maven Wrapper 3.3.4 for the single backend module. The Wrapper distribution is checksum-verified. pnpm remains the TypeScript workspace manager; root scripts orchestrate rather than replace both native tools.

## Alternatives considered

- Gradle Kotlin DSL: faster/customizable build graph, but higher DSL/plugin complexity.
- Maven 4 release candidate: promising but not production-stable at freeze time.
- Multi-module Maven immediately: unnecessary boundary/build overhead for one deployable.

## Why selected

Maven is explicit, stable, well-supported by Spring Boot, familiar to Java engineers, and sufficient for one backend artifact.

## Trade-offs

- More verbose configuration.
- Less flexible incremental/custom build logic than Gradle.
- Two ecosystem tools remain in the polyglot monorepo.

## Risks

- Root orchestration can hide failures or diverge from native commands.
- Plugin versions can drift if not centrally pinned.

## One-million-user review

No runtime bottleneck. Repository size may increase CI time, addressed by caching and test partitioning rather than build-system migration.

## Future migration path

First split Maven modules only when compile-time boundaries or build duration justify it. A Gradle migration would preserve standard source layout and CI contract, then run dual builds briefly before cutover.

## Consequences

ENG-002 commits the checksum-verified Wrapper files. When ENG-005 creates the backend POM, it must pin plugins, enforce the Maven and Java baselines, use dependency convergence checks, and prohibit ad hoc Maven profiles for business behavior.
