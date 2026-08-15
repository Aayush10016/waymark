# ADR-0019: GitHub Actions CI/CD with Build-Once Promotion

- **Status:** Accepted
- **Date:** 2026-07-05
- **Decision owners:** Platform Owner, Technical Lead

## Context

Trail’s source, reviews, issues, and release controls live in GitHub. Backend and extension have different rollout channels and risk.

## Decision

Use GitHub Actions for required PR checks and release workflows. Build immutable backend image and extension artifact once per release revision, attach provenance/checksums, and promote the tested artifacts. Use GitHub OIDC federation to cloud roles; no long-lived deploy secrets. Production/backend and Web Store promotions require explicit approval and separate rollback paths.

## Alternatives considered

- Jenkins/self-hosted CI: control, high maintenance.
- GitLab CI: capable, duplicates source platform.
- Build separately per environment: simple, weak artifact equivalence.
- Continuous auto-production deployment: fast, premature for sensitive private beta.

## Why selected

It integrates branch protection, minimizes platform overhead, and makes tested artifacts identifiable.

## Trade-offs

- GitHub/vendor/runners dependency.
- Extension review/rollout cannot be atomic with backend.
- Hosted runner cost/limits as suites grow.

## Risks

- Workflow supply-chain injection/excess permissions.
- Cache poisoning or unpinned actions.
- Backend contract breaks distributed extensions.

## One-million-user review

Possibly a team-delivery bottleneck, not runtime. Large tests/builds require partitioned runners and release orchestration.

## Future migration path

Pin actions by digest, add protected reusable workflows and ephemeral self-hosted runners if justified. Preserve build artifact/provenance contracts so CI vendor can change without release redesign.

## Consequences

Main is protected; required checks, environment approvals, least-privilege workflow permissions, contract compatibility, and staged extension rollout are mandatory.
