# ADR-0021: Superseded Capability-First Package Naming

- **Status:** Superseded by [ADR-0030](ADR-0030-package-namespace.md)
- **Date:** 2026-07-05
- **Decision owners:** Technical Lead

## Context

Java root packages and TypeScript package names become pervasive, appear in persistence/configuration/tests, and are noisy to change.

## Decision

Use root Java package `io.github.aayush10016.trail` as defined by [ADR-0030](ADR-0030-package-namespace.md), then capability (`identity`, `capture`, `problem`, `intelligence`, `retrieval`, `privacy`) and Clean Architecture layer. TypeScript workspace packages use a private `@trail/*` scope. Product language uses `ProblemSession`, `AuthSession`, `Recall`, and `GhostReplay`; new code must not use `Journey` or `RabbitHole`.

## Alternatives considered

- `app.trail`/`dev.trail`: may better match a future owned domain, but ownership is not currently documented.
- Global layer packages: weak capability ownership.
- `com.trail.backend`: redundant deployment detail in every package.
- Feature names invented during implementation: terminology drift.

## Why selected

It matches Trail v1.0 blueprint and communicates capability boundaries. A single pre-code legal/domain check is cheaper than later rename.

## Trade-offs

- The active `io.github.aayush10016.trail` namespace is longer than the original placeholder.
- Capability-first imports require dependency enforcement.
- Rename after code generation is mechanically possible but noisy.

## Risks

- Future company-domain migration may require a mechanical rename.
- `shared` and `utils` bypass capability rules.

## One-million-user review

No runtime bottleneck. Namespace/API publication makes later rename more expensive.

## Future migration path

Follow ADR-0030. After implementation/public SDKs, any company-domain migration uses IDE/mechanical migration plus compatibility packages only if unavoidable.

## Consequences

ADR-0030 removed the domain-ownership blocker. Architecture tests enforce capability dependencies.
