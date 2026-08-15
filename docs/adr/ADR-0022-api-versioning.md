# ADR-0022: URL Major API Versioning with OpenAPI Compatibility

- **Status:** Accepted
- **Date:** 2026-07-05
- **Decision owners:** API Owner, Extension Owner

## Context

Chrome Web Store rollout means older extension clients remain active while backend changes. Sensitive product semantics must not drift silently.

## Decision

Version public extension REST paths by major prefix `/api/v1`. Allow additive optional response fields within v1; breaking path, required-field, or semantic changes require `/api/v2`. OpenAPI is authoritative. Extension sends contract/app version; backend enforces a supported rollout window.

## Alternatives considered

- Header/media-type versioning: cleaner URLs, less visible/debuggable for one client.
- Date versions: useful for public platforms, unnecessary cadence.
- No versioning: simplest, unsafe for distributed extension clients.
- GraphQL schema evolution: different API paradigm and cache/security complexity.

## Why selected

URL major versions are explicit and operationally simple for a controlled extension/backend pair.

## Trade-offs

- Parallel major handlers/migrations during transitions.
- Developers may overuse v2 instead of additive design.
- URL version does not solve database/message compatibility alone.

## Risks

- Extension rollout lag strands clients.
- “Additive” fields alter semantics or security unexpectedly.
- Generated client drift.

## One-million-user review

No direct bottleneck. Client/version population and deprecation operations grow.

## Future migration path

Run v1/v2 side by side, instrument version usage content-safely, stage extension migration, enforce minimum only for security/policy reasons, then retire after active population threshold and notice.

## Consequences

CI performs OpenAPI breaking-change checks and generated-client compilation. Server kill/minimum-version controls are required before beta.
