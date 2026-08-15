# Private Beta Cohort and Support Controls v1

ENG-105 defines the private-beta cohort, support, privacy, and owner controls required before Trail v1.1 is invited into a private beta.

This document is a release-control contract. It does not deploy Render, publish the Chrome extension, submit a Chrome Web Store listing, or activate external support systems without the owner-controlled accounts.

## Cohort entry policy

Private-beta access is invite-only. A participant may be added only when all of the following are true:

- The user has explicitly opted into private-beta evaluation.
- The user has completed Trail onboarding and accepted capture consent.
- The user can revoke capture consent from the extension before any support or evaluation request.
- The support owner has provided the content-hidden support policy before requesting feedback.
- The release owner has recorded the participant using an internal cohort identifier, not public browser history, raw URLs, or captured page content.

## Consent and revoke controls

Trail private beta must preserve the v1.1 consent model:

- Capture remains disabled until explicit consent is accepted.
- Consent revocation stops capture before persistence.
- Account or deletion requests follow the deletion runbook.
- Uninstalling the extension ends local capture immediately.
- Support and evaluation workflows must never ask users to bypass revocation controls.

## Content-hidden support

Support defaults to content-hidden handling.

Support may request:

- Browser, operating system, extension version, backend environment, and timestamp range.
- Sanitized problem-session identifiers when the user chooses to provide them.
- User-written reproduction notes that exclude secrets and private URLs.

Support must not request by default:

- Full browser history.
- Raw URLs with query strings.
- Page content, form values, cookies, headers, screenshots, clipboard data, tokens, API keys, passwords, or private repository content.
- AI provider raw request or response payloads.

If a user chooses to provide a screenshot, log excerpt, or problem details, the support owner must remind the user to redact secrets, private URLs, tokens, credentials, and proprietary code first.

## Opt-in evaluation

Private-beta product evaluation is opt-in and qualitative by default.

Allowed evaluation inputs:

- Whether Trail helped the user avoid solving the same engineering problem twice.
- Whether Recall, Ghost Replay, Problem Session solve, deletion, and consent controls behaved as expected.
- User-written feedback that the participant voluntarily provides.

Disallowed evaluation inputs unless a later signed privacy review approves them:

- Telemetry collection beyond the frozen v1.1 product implementation.
- Silent analytics.
- Raw browser history import.
- Keystrokes, DOM content, clipboard data, screenshots, cookies, headers, or form values.
- AI training data export.

## Security contact

Before inviting a cohort, the security owner must publish a reachable private-beta security contact in the release checklist used by the owner.

The security contact is responsible for:

- Receiving vulnerability, privacy, authentication, deletion, capture, AI, and data-retention reports.
- Escalating incidents through the incident-response runbook.
- Confirming sensitive reports are not copied into public issue trackers.
- Verifying that security findings have an owner and severity.

## Active owner roster

Private beta requires active owners before the first invitation:

| Role | Required responsibility |
|---|---|
| Release owner | Approves cohort entry and rollout/rollback decisions. |
| Support owner | Handles content-hidden support and participant feedback. |
| Security owner | Owns vulnerability/privacy reports and incident escalation. |
| Deletion owner | Owns deletion requests and verification. |
| Platform owner | Owns Render staging/production health, backup, and rollback evidence. |

Each owner must have a reachable primary and backup contact in the private owner roster maintained outside the public repository.

## Activation checklist

Private beta is not externally active until the owner completes these actions:

- Create the invite-only cohort roster outside the repository.
- Publish the private-beta support channel.
- Publish the private-beta security contact.
- Confirm support, security, deletion, platform, and release owners are active.
- Confirm Chrome Web Store rollout is limited to the approved private-beta cohort or staged rollout.
- Confirm Render staging and release gates are verified according to ENG-101 through ENG-104.

## Deferred

ENG-105 does not publish the Chrome Web Store listing, invite participants, provision external support tools, deploy Render, execute final release sign-off, or start the 72-hour monitoring plan. ENG-106 consumes these controls during final release readiness review.
