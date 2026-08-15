# ADR-0009: OIDC PKCE with JWT Access and Rotating Opaque Refresh Sessions

- **Status:** Superseded by [ADR-0028](ADR-0028-email-password-authentication.md)
- **Date:** 2026-07-05
- **Decision owners:** Security Owner, Backend Owner

## Context

Trail must authenticate a Chrome extension without passwords, browser-page token leakage, or indefinitely valid bearer tokens.

## Decision

Use one OIDC provider with Authorization Code + PKCE/state for identity. Exchange through a one-time extension pairing flow. Issue asymmetric JWT access tokens (approximately 5–10 minutes) and opaque refresh credentials stored hashed server-side, rotated every use with family reuse detection/revocation.

## Alternatives considered

- Password authentication: larger security/recovery surface.
- Long-lived JWT only: simple, weak revocation/theft response.
- OIDC tokens directly in extension: provider coupling and audience/scope mismatch.
- Cookie web session: unsuitable as the extension API credential model.

## Why selected

It delegates identity proof, limits access-token lifetime, preserves server-side revocation, and scopes extension authority.

## Trade-offs

- Pairing and refresh rotation are complex.
- Chrome storage is not a secure enclave.
- OIDC availability/vendor dependency.

## Risks

- Refresh credential theft from compromised profile.
- Token replay/race bugs and JWT algorithm/audience confusion.
- Pairing-code interception.

## One-million-user review

Not inherently a bottleneck. Auth-session writes, revocation checks, signing-key distribution, and provider limits may require caching/partitioning.

## Future migration path

Scale session table/indexes and key distribution; add OIDC providers behind issuer mapping. If auth becomes a specialized operational burden, migrate identity proof/session issuance to a managed identity platform while preserving Trail user IDs and token audience contracts.

## Consequences

No password endpoints. Sensitive actions reauthenticate. Every API query still enforces Trail authorization independently of authentication.
