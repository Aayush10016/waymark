# ADR-0023: PostgreSQL-Backed Explicit Background Jobs

- **Status:** Accepted
- **Date:** 2026-07-05
- **Decision owners:** Backend Owner, Data/Platform Owner

## Context

Enrichment, embeddings, retention, and deletion need durable asynchronous retries. MVP has one application and PostgreSQL but no external consumers.

## Decision

Use an explicit `background_jobs` table. Insert a job in the same transaction as the triggering state. Scheduled handlers claim ready jobs with leases/`SKIP LOCKED`, call providers outside database transactions, and persist capped retry/terminal states. Initially execute in the backend process with bounded concurrency.

## Alternatives considered

- Redis queue: extra datastore and durability/eviction operations.
- Kafka/RabbitMQ: excessive infrastructure and event semantics.
- In-memory executor: loses work on restart/deploy.
- Generic transactional outbox: useful for external publication, unnecessary now.

## Why selected

It provides transactional durability and idempotent retry with the existing source of truth.

## Trade-offs

- Polling/write/lock load on PostgreSQL.
- Jobs share process resources with API.
- Less sophisticated scheduling/observability than queue platforms.

## Risks

- Long provider calls hold leases or duplicate after lease expiry.
- Hot job table/index bloat.
- Job/API resource contention.

## One-million-user review

Yes. Throughput, polling, retention, and failure isolation may become bottlenecks.

## Future migration path

First deploy a separate worker role from same code/database and tune leasing/partition/archive. If limits persist or external consumers appear, add transactional outbox and broker, dual-publish, drain DB jobs, and preserve idempotent handlers.

## Consequences

Job payloads carry IDs only; handlers reload authorized data, are idempotent, and never hold transactions during network calls.
