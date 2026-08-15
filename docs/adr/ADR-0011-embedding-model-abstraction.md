# ADR-0011: Versioned Fixed-Dimension Embedding Generations

- **Status:** Accepted
- **Date:** 2026-07-05
- **Decision owners:** AI/Retrieval Owner, Data Owner

## Context

Embedding models change semantics and dimensions. Mixing generations silently corrupts similarity, indexing, evaluation, and deletion behavior.

## Decision

Define an `EmbeddingProvider` outbound port independent of `AIProvider`. Adapters may include `GeminiEmbeddingProvider`, `OpenAIEmbeddingProvider`, `LocalEmbeddingProvider`, and `MockEmbeddingProvider`; MVP implements one selected production adapter plus mock. Configuration selects it through `trail.embedding.provider`. Represent outputs by explicit provider, model key/version, dimension, canonical-input fingerprint, policy version, and generation. One generation is active for reads. Fixed-dimension physical storage is used; incompatible dimensions require a new column/table generation.

## Alternatives considered

- Store only a vector on Problem Session: simple, no safe model migration/audit.
- Dimensionless arrays/JSON: flexible, poor indexing/type safety.
- Model-specific business logic: vendor lock-in.
- Multiple active models from launch: expensive and hard to evaluate.

## Why selected

It makes similarity reproducible and migrations explicit while keeping MVP to one model.

## Trade-offs

- More metadata and migration machinery.
- Backfills cost provider calls and storage.
- Dual-read periods complicate ranking.

## Risks

- Input canonicalization changes without versioning.
- Partial backfills produce inconsistent results.
- Old/new scores are not comparable.

## One-million-user review

Not a bottleneck; migration cost scales with solved-session count and can become substantial.

## Future migration path

Create new generation storage, batch backfill with checkpoint/budget, shadow retrieval on a labeled corpus, switch read generation atomically, retain rollback window, then delete old vectors under retention policy.

## Consequences

No model/dimension change is a configuration-only edit. It requires a reviewed migration/evaluation plan.
