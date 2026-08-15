# ADR-0008: pgvector 0.8.2

- **Status:** Accepted
- **Date:** 2026-07-05
- **Decision owners:** Data/Platform Owner, AI/Retrieval Owner

## Context

Trail needs personal solved-session vector similarity while keeping relational ownership and deletion simple.

## Decision

Use pgvector 0.8.2 on PostgreSQL 17. Pin the extension version in local/test/deployed environments. Begin without an approximate vector index.

## Alternatives considered

- Earlier pgvector release: less current and misses filtered-search improvements.
- Dedicated vector database: independent scale, additional datastore/tenant/deletion complexity.
- No vectors: simpler but cannot validate semantic Recall.
- PostgreSQL native arrays/custom similarity: inferior operations/query support.

## Why selected

It keeps vectors with canonical ownership and supports exact and later HNSW/IVFFlat paths. Version 0.8.x includes iterative scan support relevant to filtered approximate retrieval.

## Trade-offs

- Vector workloads share PostgreSQL resources.
- Fixed dimension complicates model migration.
- Filtered approximate recall requires careful benchmarks.

## Risks

- Managed provider may not expose the pinned extension version.
- Approximate indexes can under-return after user filtering.
- Extension upgrade behavior must be tested like a schema dependency.

## One-million-user review

Potential bottleneck when global vector corpus/index size harms OLTP or filtered recall/latency misses targets.

## Future migration path

First use exact per-user scans, then benchmark HNSW with iterative scans/overfetch. If PostgreSQL still fails, dual-write embeddings to a dedicated vector/search service, shadow queries, compare recall, and cut reads over while PostgreSQL remains canonical.

## Consequences

Provider selection must verify PostgreSQL 17 + pgvector 0.8.2 availability before deployment approval.
