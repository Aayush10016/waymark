# ADR-0024: TanStack Query for Server State and Zustand for Ephemeral UI State

- **Status:** Accepted
- **Date:** 2026-07-05
- **Decision owners:** Frontend Owner

## Context

Popup, side panel, onboarding, and full-page app share server-backed data, local drafts, and short-lived UI state. Duplicate caches create stale Recall behavior.

## Decision

Use TanStack Query for server state, fetching, cache, mutations, invalidation, and degradation. Use Zustand only for ephemeral cross-component UI state. Persisted queue/settings/drafts remain behind extension storage adapters; controlled forms own active input.

## Alternatives considered

- Redux Toolkit for all state: powerful, duplicates server cache and adds ceremony.
- Zustand for all state: simple, manual server synchronization/invalidation.
- React Context only: adequate small state, rerender/organization pressure across surfaces.
- TanStack Query only: poor fit for complex ephemeral UI transitions.

## Why selected

It gives explicit ownership: server cache, UI state, and durable browser storage do not compete.

## Trade-offs

- Engineers must choose the correct owner.
- Cross-surface caches are separate runtime instances.
- Query hydration/offline behavior needs design.

## Risks

- Copying query data into Zustand.
- Optimistic updates conflict with local drafts/server versions.
- Broad invalidation causes request storms.

## One-million-user review

No centralized bottleneck. Large client datasets and many surfaces require cache limits and query discipline.

## Future migration path

Add persisted query subsets only for explicit offline needs, virtualize large lists, and introduce event-driven invalidation only if polling/staleness proves inadequate. State ownership remains stable if libraries change.

## Consequences

Query keys are feature-owned factories; direct Chrome/storage calls are prohibited in React feature components.
