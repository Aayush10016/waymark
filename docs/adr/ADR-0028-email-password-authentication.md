# ADR-0028: Email/Password Authentication with JWT Access and Rotating Refresh Tokens

- **Status:** Accepted; supersedes ADR-0009
- **Date:** 2026-07-05
- **Decision owners:** Security Owner, Backend Owner

## Context

Trail v1.1 simplifies MVP authentication and removes OIDC, PKCE, and social login.

## Decision

Use normalized verified email plus password credentials. Hash passwords with Spring Security Argon2id using benchmarked parameters. Issue short-lived asymmetric JWT access tokens and opaque refresh tokens stored hashed server-side, rotated on every use with family reuse detection and revocation. Support registration, login, refresh, logout, email verification, forgot-password, and single-use expiring reset tokens. Social login is deferred.

## Alternatives considered

- OIDC/social login: less password handling, more provider/pairing complexity.
- Passwordless magic links: simple credentials, email-delivery/security dependency on every login.
- Long-lived JWTs: simple, weak revocation.
- Sessions/cookies: workable for web apps, less natural for extension bearer API.

## Why selected

It is understandable, provider-independent, and meets the explicitly requested MVP scope while retaining secure token rotation.

## Trade-offs

- Trail owns password, verification, reset, brute-force, and account-recovery security.
- Email delivery becomes an operational dependency.
- More sensitive credential data than OIDC.

## Risks

- Credential stuffing, enumeration, weak passwords, reset-token theft, refresh-token theft.
- Chrome storage is not a secure enclave.

## One-million-user review

Not inherently a bottleneck. Hash cost, login abuse, email delivery, session writes, and key distribution require capacity controls.

## Future migration path

Add social OIDC providers by linking verified identities to the stable user ID; never key ownership by email. Managed identity migration can import password hashes only if compatible or force secure reset. Existing JWT audience/session contracts remain stable.

## Consequences

Identity tables and APIs must include email verification/reset. Authentication endpoints use generic responses, rate limits, breached-password policy where feasible, and no account-existence disclosure.
