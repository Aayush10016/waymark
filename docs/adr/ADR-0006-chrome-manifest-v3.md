# ADR-0006: Chrome Manifest V3 Extension

- **Status:** Accepted
- **Date:** 2026-07-05
- **Decision owners:** Extension Owner, Security Owner

## Context

Trail requires top-level navigation metadata, a toolbar popup, side panel, durable local queue/drafts, and Web Store distribution.

## Decision

Use Chrome Manifest V3 with an ephemeral service worker, popup, side panel, onboarding/full-page extension pages, `webNavigation`, `tabs`, `storage`, `alarms`, and `sidePanel`. Email/password authentication requires no Chrome `identity` permission. No content scripts, Incognito, history import, externally connectable messaging, or remote code in MVP.

## Alternatives considered

- Manifest V2: deprecated/unacceptable for new Web Store releases.
- Content-script overlay: richer in-page UX, materially higher permission/security risk.
- Native desktop/IDE app: broader context, different product/distribution scope.
- Browser history permission: easier backfill, larger trust cost.

## Why selected

MV3 is the supported Chrome platform and side panel enables Recall beside current work without page injection.

## Trade-offs

- Service worker suspension complicates state and retries.
- Chrome-only platform dependency.
- Metadata lacks code/terminal/full AI-chat context.

## Risks

- Store policy or API behavior changes.
- Permission warnings reduce conversion.
- Worker/local/server state divergence.

## One-million-user review

Extension runtime is per-device, not a centralized throughput bottleneck. Chrome platform concentration and store rollout compatibility become business/operational risks.

## Future migration path

Maintain browser adapters and contract versions. Add Chromium/Firefox or IDE clients only through explicit product ADRs. Content scripts require separate privacy/threat review and optional permissions.

## Consequences

All correctness state persists durably. ENG-008 must validate exact permissions, user gestures, and supported Chrome versions before implementation.
