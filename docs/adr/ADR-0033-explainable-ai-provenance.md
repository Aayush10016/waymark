# ADR-0033: Explainable AI Provenance and Recommendation Evidence

- **Status:** Accepted
- **Date:** 2026-07-05
- **Decision owners:** AI/Retrieval Owner, Security Owner

## Context

Users must understand why Trail recommended a prior solution. Provider/model/prompt/vector changes must be reproducible without storing unrestricted payloads.

## Decision

Persist structured provenance for every enrichment, embedding, and recommendation: provider, model, prompt key/version/hash, output schema, policy version, timestamp, input fingerprint, embedding generation, retrieved Problem Session IDs/ranks, evidence codes and referenced artifact/session IDs, algorithm version, generated summary, and status/cost metadata. Do not store raw provider request/response by default. User-facing explanations are deterministic from evidence codes.

## Alternatives considered

- Raw prompt/response logging: maximum debug detail, unacceptable sensitive-data exposure.
- Scores only: compact, not explainable or reproducible.
- Generated natural-language rationale: can hallucinate reasons.
- No provenance: impossible audits/regressions.

## Why selected

Structured provenance supports “Why this?” explanations, evaluation, deletion, incident review, and model migrations while minimizing duplicated sensitive text.

## Trade-offs

- Additional schema/storage and careful retention.
- Explanation quality is limited to recorded evidence.
- IDs require source records to remain available or be tombstoned safely.

## Risks

- Provenance itself leaks sensitive relationships.
- Generated summary duplicates sensitive text.
- Evidence metadata drifts from ranking implementation.

## One-million-user review

Storage grows with AI/recommendation activity but is manageable with retention/partitioning/aggregation. High-cardinality debugging queries must remain indexed and bounded.

## Future migration path

Partition/archive provenance by time, retain compact audit summaries, and move analytical copies to a privacy-reviewed warehouse if required. Preserve source IDs/algorithm versions across search-service migrations.

## Consequences

Recommendation APIs return explanation codes/evidence, never black-box scores. Deletion removes or irreversibly detaches user-owned provenance according to policy.
