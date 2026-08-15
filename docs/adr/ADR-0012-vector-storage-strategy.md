# ADR-0012: PostgreSQL pgvector with Exact User-Scoped Search First

- **Status:** Accepted
- **Date:** 2026-07-05
- **Decision owners:** Data Owner, AI/Retrieval Owner

## Context

Each user initially has a small solved-problem corpus. Trail needs strict ownership/deletion and hybrid relational/vector retrieval.

## Decision

Store vectors in a dedicated `problem_embeddings` table in canonical PostgreSQL. Begin with exact vector distance constrained by `user_id` and active model generation. Add HNSW only after measured latency/corpus thresholds and filtered-recall evaluation.

## Alternatives considered

- HNSW from day one: faster large-corpus reads, premature memory/build/filtered-recall complexity.
- Dedicated vector database: scalable, extra consistency/deletion/operations.
- Event-level embeddings: more granular, much higher cost/corpus/noise.
- Lexical only: cannot validate semantic recurrence.

## Why selected

Personal corpora are small; exact search is correct, simple, transactionally deletable, and easy to benchmark.

## Trade-offs

- Exact work grows linearly within user corpus.
- Vector load shares OLTP resources.
- Cross-user indexing cannot compromise tenant scope.

## Risks

- A few heavy users cause latency skew.
- HNSW with post-filtering can miss results.
- Vector queries can leak across tenants if ownership is omitted.

## One-million-user review

Yes, potentially. Total corpus/index memory and concurrent vector queries may harm PostgreSQL even if each user corpus is small.

## Future migration path

Optimize exact queries and connection limits; add HNSW/iterative scans with overfetch after recall benchmarks. If OLTP impact persists, dual-write to a dedicated vector/search service, shadow/evaluate, cut reads over, and retain PostgreSQL as canonical/deletion ledger.

## Consequences

Every vector query includes user and generation scope in SQL. Approximate indexing is prohibited without an ADR update backed by measurements.
