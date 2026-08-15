# ADR-0013: No General Server Cache in MVP

- **Status:** Accepted
- **Date:** 2026-07-05
- **Decision owners:** Technical Lead, Frontend Owner

## Context

Trail needs responsive extension surfaces but has small private-beta traffic and sensitive personalized data. Cache invalidation and Redis add operational/security complexity.

## Decision

Use TanStack Query for in-surface server-state caching, bounded IndexedDB caches for offline UX, and normal PostgreSQL/HTTP caching semantics where safe. Do not deploy Redis or a shared server cache in MVP. Never cache authorization decisions beyond token validity.

## Alternatives considered

- Redis from launch: easy counters/cache/jobs, unnecessary datastore.
- In-process backend cache: simple, inconsistent across replicas and risky for sensitive data.
- No client cache: excessive requests and poor offline UX.
- Service worker CacheStorage: optimized for request/response assets, not Trail structured state.

## Why selected

It keeps personalized cache close to the user, avoids distributed invalidation, and preserves a simple source of truth.

## Trade-offs

- More database reads.
- Client caches require explicit invalidation/versioning.
- Multi-instance rate limits may initially use database/provider controls.

## Risks

- Stale candidate/recommendation UI.
- Sensitive cache persists on device.
- Database hot reads at scale.

## One-million-user review

Yes, database read pressure, rate-limit coordination, and repeated safe derivations may justify a shared cache.

## Future migration path

Measure query/load hotspots. Introduce Redis only for named workloads with TTL, encrypted transport, user-scoped opaque keys, bounded values, eviction/failure policy, and source-of-truth independence. Do not move canonical jobs/data into cache.

## Consequences

Cache ownership/invalidation is feature-specific. No generic cache abstraction until a second backend cache use case exists.
