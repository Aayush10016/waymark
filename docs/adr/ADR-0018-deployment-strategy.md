# ADR-0018: Single-Region Managed Container Deployment

- **Status:** Superseded by [ADR-0029](ADR-0029-render-deployment.md)
- **Date:** 2026-07-05
- **Decision owners:** Technical Lead, Platform Owner

## Context

Trail needs private-beta deployment with minimal operations, secure networking, backups, rolling releases, and a path to horizontal API scaling.

## Decision

Deploy the single backend container to a managed container runtime in one primary region behind managed HTTPS/load balancing. Use managed PostgreSQL 17 with private networking, encryption, PITR/backups, connection pooling, secret manager/KMS, and static extension distribution through Chrome Web Store. No Kubernetes or active-active multi-region.

## Alternatives considered

- VMs: more control/operations.
- Kubernetes: scalable platform, excessive team burden.
- Serverless functions: awkward long-running Spring/container/jobs/connections.
- Active-active multi-region: high consistency/deletion complexity.

## Why selected

It minimizes undifferentiated operations while supporting stateless horizontal replicas and managed data durability.

## Trade-offs

- Cloud/runtime vendor dependency.
- Regional outage affects service.
- Background jobs share deployment initially.

## Risks

- Vendor/region selection affects residency, pgvector support, cost, egress, restore.
- Connection limits under horizontal scale.
- Job/API resource contention.

## One-million-user review

Yes. One region/primary may become latency, residency, availability, and write-throughput bottleneck.

## Future migration path

First add multi-zone replicas, independent worker role, connection controls, read replicas, and tested regional recovery. Add regional data planes/residency or sharding by user only from contractual/latency evidence; avoid global active-active until required.

## Consequences

B4 must select provider/region after verifying PostgreSQL 17, pgvector 0.8.2, private networking, secret/KMS, backup/restore, and cost.
