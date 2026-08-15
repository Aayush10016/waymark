# ADR-0014: IndexedDB and chrome.storage.local by Responsibility

- **Status:** Accepted
- **Date:** 2026-07-05
- **Decision owners:** Extension Owner, Security Owner

## Context

MV3 workers are ephemeral. Trail must preserve queues and typed solutions across suspension/restart/offline while keeping settings simple.

## Decision

Use `chrome.storage.local` for small settings, policy/consent version, capture state, and minimal auth-session metadata. Use IndexedDB for capture queue, solution drafts, bounded cached Problem Sessions/recommendations, and schema migrations. Do not use localStorage, sync storage, or CacheStorage for product data.

## Alternatives considered

- chrome.storage.local for everything: simple API, poor large/transactional queue behavior.
- IndexedDB for everything: transactional but cumbersome for tiny settings.
- chrome.storage.sync: cross-device convenience, quota/privacy/uncontrolled sync.
- localStorage: unavailable/inappropriate in service workers and non-transactional.

## Why selected

It matches data shape and lifecycle while tolerating worker termination.

## Trade-offs

- Two storage APIs and migration paths.
- IndexedDB complexity.
- Neither is a secure enclave.

## Risks

- Profile compromise exposes cached data/refresh material.
- Quota/migration failures lose capture unless handled.
- Local/server reconciliation races.

## One-million-user review

No centralized bottleneck. Heavy individual histories/device quotas can become product limits.

## Future migration path

Enforce caps/retention, virtualize caches, and store only server-recoverable summaries. If offline requirements grow, introduce a versioned local database repository without changing feature contracts. Never silently evict final-solution drafts.

## Consequences

All storage access goes through adapters; React components and Chrome listeners do not manipulate raw stores directly.
