# Scalability

## MVP scale posture

Optimize for reliable private beta, not hypothetical millions. A solved Problem Session is small, and each user’s personal vector corpus is initially tiny.

Planning target for the first architecture:

- at most 1,000 active beta users;
- up to 300 eligible events per active user/day before retention;
- up to 10 solved Problem Sessions per active user/week;
- one enrichment and embedding per solved session;
- bounded current-candidate embeddings only after debugging detection;
- low-frequency proactive and manual retrieval.

These are stress assumptions, not forecasts.

## Expected bottlenecks

1. Raw capture-event write volume.
2. Model cost if current candidates are embedded too often.
3. False recommendations, which are a product bottleneck before a compute bottleneck.
4. pgvector filtering as users and solved-session counts grow.
5. Extension reconnect bursts.

## Initial scaling approach

- Batch capture events.
- Keep raw-event indexes minimal and purge by expiry.
- Recompute candidate context only for a recent bounded window.
- Fingerprint enrichment input and avoid duplicate model work.
- Use PostgreSQL jobs with `SKIP LOCKED` semantics when multiple application instances are introduced.
- Start exact vector comparison within user scope; personal corpora are small.
- Apply timeouts and lexical fallback.
- Add modest horizontal API scaling only after the single application instance is measured.

## Why no Redis

Rate limits, short coordination, and durable jobs can be handled with application memory plus PostgreSQL at private-beta scale. Redis becomes justified only when database-backed jobs or multi-instance coordination miss measured latency/throughput targets.

## Why no WebSockets

Candidate closure and enrichment are not real-time collaborative events. Polling on extension open and bounded alarms is sufficient. A persistent socket adds extension lifecycle, authentication, and infrastructure complexity without improving the killer moment.

## Why no graph database

MVP queries are session detail, personal lexical/vector retrieval, and ordered artifacts. No deep traversal exists. PostgreSQL relations are sufficient.

## Scale triggers

| Component | Add only when |
|---|---|
| Separate worker deployment | AI/deletion work affects API reliability despite PostgreSQL job isolation |
| Redis/message broker | Database jobs or coordination miss measured requirements |
| Approximate vector index | Exact user-scoped comparison exceeds latency target at real corpus size |
| Dedicated vector/search service | PostgreSQL cannot meet measured recall/latency after tuning |
| Object storage | Explicit files or large exports become validated requirements |
| Multi-region | Residency, latency, or recovery requirements are contractual |
| Graph database | A valuable product query requires graph traversal |

## Performance targets

- Capture batch p95 under 500 ms at beta load.
- State/recommendation read p95 under 500 ms excluding provider calls.
- Similarity lookup p95 under 750 ms after current embedding exists.
- Exact/lexical solved-problem search p95 under 300 ms for the private-beta corpus.
- Ghost Replay cached/detail payload visible within 200 ms after side-panel data is locally available; network retrieval target p95 under 500 ms.
- Backend cold startup/readiness target under 30 seconds on the selected Render plan; startup is measured before tuning.
- Heap/container memory is measured under capture bursts and AI job concurrency; concurrency is capped before increasing instance size.
- AI work remains asynchronous; no model call blocks saving a final solution.
- Raw-event retention cleanup completes without long table locks.

## Database growth controls

- Raw capture events dominate growth and expire in bounded chunks.
- Solved-session artifacts and provenance use explicit retention/index review.
- Index size, dead tuples, autovacuum lag, connection saturation, job-table size, and backup duration are monitored through lightweight metrics and database tooling.
- Do not add partitioning, read replicas, HNSW, Redis, or a search service until a documented threshold is missed after query/index/concurrency tuning.

## Cost metric

Track infrastructure and AI cost per **helpful recalled solution**, not per captured event or generated summary. If the product cannot create helpful recalls, cost optimization is irrelevant.
