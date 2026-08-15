# ADR-0030: `io.github.aayush10016.trail` Java Package Namespace

- **Status:** Accepted; supersedes ADR-0021
- **Date:** 2026-07-05
- **Decision owners:** Technical Lead

## Context

Commercial Trail domains are not required to establish a globally unique Java namespace for the open-source project. Requiring ownership of one would create an implementation dependency unrelated to product validation. The project is owned by GitHub user `aayush10016`, which provides a stable identity suitable for the established reverse-host namespace convention.

## Decision

Use root Java package `io.github.aayush10016.trail`. Use capability-first children: `io.github.aayush10016.trail.identity`, `io.github.aayush10016.trail.capture`, `io.github.aayush10016.trail.problem`, `io.github.aayush10016.trail.intelligence`, `io.github.aayush10016.trail.retrieval`, and `io.github.aayush10016.trail.privacy`. TypeScript packages remain private `@trail/*` packages.

## Alternatives considered

- `app.trail`: concise, but requires control of `trail.app` to preserve reverse-DNS ownership semantics.
- `com.trail`: generic and potentially unowned.
- `io.trail`: implies ownership of `trail.io`.
- `com.trailapp`: brand-like but not reverse DNS.
- Per-service roots: unnecessary and inconsistent.

## Why selected

It is globally unique, follows the common Java convention for GitHub-hosted open-source projects, is tied to the project owner, and removes commercial domain ownership as an implementation blocker.

## Trade-offs

- The namespace is longer than a commercial reverse-domain package.
- It ties published Java types to the project owner's current GitHub identity.

## Risks

- The owner changes their GitHub username or transfers the project.
- Trail later becomes a company and adopts a company-owned domain namespace.
- Published packages make a later rename noisy.

## One-million-user review

No runtime bottleneck. Package names do not affect request throughput, storage, or deployment topology.

## Future migration path

If Trail later adopts a company-owned domain, introduce its reverse-domain namespace through a deliberate mechanical migration. Before public Java APIs exist, rename directly. After publication, use a major version, temporary compatibility facades where justified, automated import migration guidance, and a defined deprecation window.

## Consequences

All blueprint/package examples and architecture tests use `io.github.aayush10016.trail`. No commercial domain ownership proof is required. This decision completes the ENG-001 namespace gate.
