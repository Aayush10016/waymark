# ADR-0004: Polyglot Monorepo

- **Status:** Accepted
- **Date:** 2026-07-05
- **Decision owners:** Technical Lead

## Context

Backend, extension, API contracts, shared UI/message packages, infrastructure definitions, and documentation change together and are owned by one startup team.

## Decision

Use one repository with `apps/backend`, `apps/extension`, named `packages`, `contracts`, `docs`, `infra`, `scripts`, and `.github`. Do not add a generic root `shared` directory.

## Alternatives considered

- Separate backend/extension repositories: independent lifecycles but cross-contract coordination overhead.
- One flat repository: simple initially, poor ownership and tooling boundaries.
- Bazel/Nx-managed monorepo: powerful graph/caching, excessive foundation complexity.

## Why selected

Atomic contract changes, one review trail, shared CI/security policy, and simpler coordination outweigh repository size at startup scale.

## Trade-offs

- CI can become broad/slow.
- Access control is repository-wide.
- Cross-boundary imports require enforcement.

## Risks

- Shared packages become dependency dumping grounds.
- Root scripts become a bespoke build system.

## One-million-user review

User count does not affect repository shape. Team/repository scale can bottleneck CI and ownership.

## Future migration path

Add path-aware CI, remote caches, CODEOWNERS, and build graph tooling from measurements. Split a deployable repository only when independent security/access/release ownership outweighs atomic changes; retain contracts as versioned artifacts.

## Consequences

Package public APIs and dependency rules must be enforced. Generated artifacts have single authoritative locations.
