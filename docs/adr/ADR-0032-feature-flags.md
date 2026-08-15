# ADR-0032: Typed Configuration Feature Flags

- **Status:** Accepted
- **Date:** 2026-07-05
- **Decision owners:** Technical Lead

## Context

AI Suggestions, Ghost Replay, Semantic Search, and explicitly experimental behavior need safe global enable/disable controls without a flag platform.

## Decision

Define a typed `FeatureFlag` enum and `FeatureFlagService` port backed by validated Spring configuration/environment values. Initial flags are global per environment, default safe/off for unfinished behavior, and evaluated server-side. Flags have owner, purpose, default, creation date, and removal condition. No admin UI, user targeting, percentage rollout, or third-party service in MVP.

## Alternatives considered

- Hard-coded booleans: fast, poor operational control.
- Database flags: runtime updates, added persistence/admin/cache semantics.
- LaunchDarkly/Unleash: powerful, unnecessary cost/complexity.
- Client-only flags: insecure and behaviorally inconsistent.

## Why selected

It provides kill switches and environment control using existing configuration while remaining easy to remove or replace.

## Trade-offs

- Changes require configuration update/restart/redeploy.
- No cohorts or percentage rollout.
- Stale flags can accumulate.

## Risks

- Security/privacy logic accidentally hidden behind optional flags.
- Contradictory flag combinations.
- Flags become permanent architecture.

## One-million-user review

Global flags are not a throughput bottleneck but become insufficient for experiments/cohorts.

## Future migration path

Preserve `FeatureFlagService`; replace provider with database or managed service only when targeting/audit/runtime-update requirements are real. Add typed context deliberately and migrate flags individually.

## Consequences

Authentication, authorization, sanitation, consent, and deletion controls are never flag-disabled. Experimental flags do not change persisted schema semantics without migration compatibility.
