# Retrieval Benchmark v1

Status: ENG-094 fixture.

## Purpose

This fixture documents the v1.1 private-beta retrieval target before adding approximate vector indexes or a separate search service.

## Targets

| Path | Target |
|---|---:|
| Solved-session lexical search | p95 under 300 ms for private-beta corpus |
| Exact user-scoped vector candidate retrieval | p95 under 750 ms after current embedding exists |

The fixture uses PostgreSQL 17 with pgvector 0.8.2 through Testcontainers and verifies query plans under skewed-user data.

## Index policy

Trail starts with exact per-user pgvector comparison. HNSW remains prohibited until exact retrieval misses the documented target after query, index, and concurrency tuning. ENG-094 therefore asserts that no HNSW index exists.

## Skew model

The benchmark seeds one larger user corpus and smaller neighboring-user data to verify user-scoped plans. The benchmark is intentionally modest so it can run in CI without becoming a long-running load test.

## Interpretation

Passing this fixture does not prove one-million-user scalability. It proves the current schema and query shape remain inside v1.1 thresholds for the private-beta corpus and provides a baseline before future tuning.
