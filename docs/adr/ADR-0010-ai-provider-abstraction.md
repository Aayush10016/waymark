# ADR-0010: Narrow AI Provider Abstraction

- **Status:** Accepted
- **Date:** 2026-07-05
- **Decision owners:** AI/Retrieval Owner, Security Owner

## Context

Trail uses AI only for structured enrichment and embeddings. Provider terms, models, cost, latency, and availability can change.

## Decision

Define a narrow `AIProvider` outbound port for structured enrichment. Provider adapters may include `GeminiAIProvider`, `OpenAIAIProvider`, `OllamaAIProvider`, and `MockAIProvider`, but MVP implements only the selected production adapter plus mock. Configuration selects the adapter through `trail.ai.provider`. Business services never import provider SDK types. Embeddings use the separate ADR-0011 abstraction. Prompts, schemas, policies, fingerprints, costs, and versions are Trail-owned.

## Alternatives considered

- Provider calls directly from application services: fastest, creates lock-in/test leakage.
- Generic AI framework/agent abstraction: broad, unnecessary complexity.
- Multi-provider routing from day one: operational/evaluation scope without evidence.
- No abstraction: cheap now, costly at first provider change.

## Why selected

One stable structured-enrichment capability isolates vendor churn without creating a lowest-common-denominator AI platform or coupling embeddings to text generation.

## Trade-offs

- Adapter/interface maintenance.
- Provider-specific advantages require explicit extension.
- One provider still creates runtime/vendor concentration in MVP.

## Risks

- Abstraction leaks provider concepts or becomes overly generic.
- Output differences undermine evaluation during migration.
- Provider retention/training terms may be unacceptable.

## One-million-user review

No architectural bottleneck; provider quotas/cost/latency are. One-provider concentration becomes operational risk.

## Future migration path

Implement a second adapter only after offline equivalence/privacy/cost review. Shadow bounded opt-in requests, compare structured quality/embedding retrieval, dual-write derivations/vectors, then switch configured reads.

## Consequences

Exact provider selection remains a pre-AI milestone blocker, not an ENG-001 blocker. No chatbot/agent/tool interface is permitted.
