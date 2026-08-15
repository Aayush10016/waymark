# ADR-0029: Render as MVP Deployment Platform

- **Status:** Accepted; supersedes ADR-0018
- **Date:** 2026-07-05
- **Decision owners:** Technical Lead, Platform Owner

## Context

Trail needs simple Docker deployment, managed PostgreSQL with pgvector, private networking, health checks, backups, and a portable path without Kubernetes.

## Decision

Deploy the backend Docker image as a Render web service in one region. Use Render Postgres through its internal/private URL with pgvector enabled, managed backups, connection pooling, and restricted external access. Configure the readiness health path for deployment checks. Keep Dockerfile, environment contract, Flyway, and standard PostgreSQL portable.

## Alternatives considered

- Railway: excellent simplicity, but Render has clearer documented managed PostgreSQL/pgvector, health-check, private-network, and scaling controls for this design.
- AWS/GCP/Azure primitives: more control, substantially more platform work.
- Kubernetes: unnecessary.
- Serverless functions: poor fit for Spring Boot connections/jobs.

## Why selected

Render directly supports Docker, internal networking, health checks, and managed PostgreSQL with pgvector while keeping operational surface small.

## Trade-offs

- Vendor-specific environment/deploy configuration.
- Single-region availability.
- Platform plan limits/cost may change.

## Risks

- Public database access left enabled or broadly allowlisted.
- PostgreSQL/pgvector version availability differs from local pins.
- Background jobs share web-service resources initially.

## One-million-user review

Yes. Platform/region/database limits may require worker separation or cloud migration well before one million active users.

## Future migration path

Keep stateless OCI image, environment variables, standard health endpoints, PostgreSQL/Flyway, and object-free runtime. Migrate by provisioning managed PostgreSQL/container runtime elsewhere, logical replication or controlled restore, DNS cutover, and rollback window. Kubernetes remains evidence-gated.

## Consequences

Before deployment, verify Render supports PostgreSQL 17 and required pgvector version/operations. Actuator exposure and database external access must be restricted.
