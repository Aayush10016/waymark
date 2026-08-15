# ADR-0005: Modular Monolith with Clean Architecture Boundaries

- **Status:** Accepted
- **Date:** 2026-07-05
- **Decision owners:** Technical Lead, Backend Owner

## Context

Trail has coupled transactional workflows but distinct identity, capture, Problem Session, intelligence, retrieval, and privacy capabilities.

## Decision

Deploy one Spring Boot application. Package by capability; within each capability use domain, application, inbound adapter, outbound adapter, and configuration layers only where they provide a real boundary. Dependencies point inward.

## Alternatives considered

- Microservices: independent scaling, unacceptable distributed/operational complexity.
- Global layer packages (`controller/service/repository`): simple initially, weak capability ownership.
- Traditional CRUD/JPA domain: faster scaffolding, leaks persistence/framework concerns into invariants.

## Why selected

It preserves transactional simplicity and developer speed while creating extraction seams for measured future needs.

## Trade-offs

- Requires architectural discipline and dependency tests.
- Can devolve into ceremony or a tightly coupled monolith.
- One deployable shares failure/scaling domain.

## Risks

- Cross-capability repository access.
- Excessive DTO/mapper/port layers for trivial reads.
- AI jobs affecting API reliability.

## One-million-user review

Potential bottleneck if ingestion, AI jobs, and reads require independent scaling or failure isolation. A monolith can still serve one million users with horizontal replicas if the database/work split is healthy.

## Future migration path

First extract worker process roles from the same codebase; then extract only measured capabilities using explicit application contracts/outbox delivery. PostgreSQL remains canonical during strangler migration.

## Consequences

Use architecture tests, capability owners, and explicit application APIs. Empty Clean Architecture directories are prohibited.
