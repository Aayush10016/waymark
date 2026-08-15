# ADR-0017: OCI Containers with Docker for Local and Build Workflows

- **Status:** Accepted
- **Date:** 2026-07-05
- **Decision owners:** Platform Owner

## Context

Trail needs reproducible backend packaging and local PostgreSQL/pgvector without committing to Kubernetes or a specific cloud.

## Decision

Package the backend as one minimal OCI image built with a multi-stage, pinned-base process and non-root runtime. Use Docker Compose only for local dependencies/workflow. Use pinned PostgreSQL 17 + pgvector 0.8.2 images for local/test; build once and promote the same backend image.

## Alternatives considered

- JAR deployment on hosts: simple, less environment consistency.
- Cloud-native buildpacks: strong defaults/SBOM, less explicit control; viable later.
- Kubernetes from launch: unnecessary orchestration.
- Dockerize extension build/runtime: build may be containerized in CI; extension runtime is Chrome.

## Why selected

OCI images are portable and match managed container services while Compose keeps local setup bounded.

## Trade-offs

- Image/base patch maintenance.
- Docker dependency for integration tests/local database.
- Multi-architecture builds may add time.

## Risks

- Floating tags/supply-chain vulnerabilities.
- Secrets copied into layers.
- Local Compose diverges from managed production.

## One-million-user review

No inherent bottleneck; container startup/resources and orchestration become deployment concerns.

## Future migration path

Add signed provenance/SBOM and multi-arch builds. Move from managed container service to Kubernetes only when service count, scheduling, or platform controls justify it; image contract remains unchanged.

## Consequences

Pin digests, scan images, run non-root/read-only where feasible, and never bake secrets or production data into images.
