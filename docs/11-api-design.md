# API Design

The authoritative detailed review is [Engineering Blueprint §6](18-engineering-blueprint.md#6-api-design-review-and-target-contract).

## Conventions

- Private REST/JSON API under `/api/v1`.
- Plural kebab-case resource paths; camelCase JSON; uppercase snake-case enums.
- Opaque UUIDs and RFC 3339 UTC timestamps.
- RFC 9457 Problem Details with stable `code` and opaque `requestId`.
- Cursor pagination; default 20 and maximum 50.
- `Idempotency-Key` where replay can duplicate effects.
- ETag/`If-Match` for optimistic Problem Session writes.
- Sensitive search and credential fields use request bodies and never access-log URLs.
- Bean validation plus explicit domain validation; unknown security-sensitive write fields are rejected.
- No public API, GraphQL, webhooks, streaming, or WebSockets.

## Authentication

| Method/path | Purpose |
|---|---|
| `POST /api/v1/registrations` | Create an unverified email/password account |
| `POST /api/v1/email-verification-requests` | Request another generic verification response |
| `POST /api/v1/email-verifications` | Consume a single-use verification token |
| `POST /api/v1/auth-sessions` | Authenticate email/password; issue access and refresh credentials |
| `POST /api/v1/auth-sessions/refresh` | Rotate opaque refresh credential and issue access JWT |
| `DELETE /api/v1/auth-sessions/current` | Revoke current extension session |
| `POST /api/v1/password-reset-requests` | Return generic acceptance without account enumeration |
| `POST /api/v1/password-resets` | Consume a single-use reset token and revoke existing sessions |

Authentication endpoints have stricter IP/account/device rate limits. Social/OIDC endpoints do not exist in v1.1.

## Capture

| Method/path | Purpose |
|---|---|
| `POST /api/v1/capture-batches` | Upload bounded sanitized events with accepted/duplicate/rejected results |
| `GET /api/v1/capture-state` | Current candidate, prompt eligibility, recommendation summary, policy freshness |

A processed batch returns 200. Use 202 only if the entire request is merely queued and no per-event result is available.

## Problem Sessions

| Method/path | Purpose |
|---|---|
| `POST /api/v1/problem-sessions` | Manually start a candidate |
| `GET /api/v1/problem-sessions` | Cursor list; solved/recent by default |
| `GET /api/v1/problem-sessions/{id}` | Detail and ordered artifacts |
| `PATCH /api/v1/problem-sessions/{id}` | Edit allowlisted fields with version precondition |
| `DELETE /api/v1/problem-sessions/{id}` | Delete canonical and derived session data |
| `POST /api/v1/problem-sessions/{id}/finish` | Close active/manual boundary |
| `PUT /api/v1/problem-sessions/{id}/solution` | Idempotently save exact user-authored solution and solved state |
| `POST /api/v1/problem-sessions/{id}/deferrals` | Record the single allowed reminder deferral |
| `GET /api/v1/problem-sessions/{id}/recommendation` | Top eligible prior solution with explanation evidence, or 204 |
| `DELETE /api/v1/problem-sessions/{id}/artifacts/{artifactId}` | Remove an artifact during candidate review |

No AI endpoint can invoke the solution command. Do not mix colon actions with noun resources.

## Search and recommendation events

| Method/path | Purpose |
|---|---|
| `POST /api/v1/problem-session-searches` | Sensitive solved-session lexical/hybrid search |
| `POST /api/v1/recommendations/{id}/events` | One allowlisted opened/helpful/not-related/dismissed event |

Search response includes results, deterministic evidence, opaque cursor, and `HYBRID` or `LEXICAL` degradation mode. It never exposes raw vector/model scores. Recommendation detail includes evidence codes and referenced prior-session/artifact IDs sufficient for `Why this?`.

## Privacy and operations

| Method/path | Purpose |
|---|---|
| `GET /api/v1/privacy-policy` | Current machine-readable capture/AI policy |
| `POST /api/v1/consent-grants` | Record explicit consent |
| `POST /api/v1/consent-withdrawals` | Withdraw and stop processing |
| `DELETE /api/v1/account` | Reauthenticate, revoke access, and begin account deletion |
| `GET /api/v1/account-deletion` | Deletion status when asynchronous |

Actuator endpoints are outside `/api/v1`. Public exposure is limited to minimal health/liveness/readiness as deployment requires; metrics is private/authenticated. Domain exclusions remain local in MVP.

## Filtering and pagination

- Problem list filters: status, created date range, technology, problem type.
- Default stable order: `updatedAt DESC, id DESC`.
- Search cursor pins query/filter fingerprint and algorithm/index version.
- Cursor cannot change user scope or filters and expires.
- Filter/page-size allowlists are validated; maximum page size 50.

## Security and compatibility

- Every repository/search/vector query scopes authenticated user in SQL.
- Passwords and tokens are never returned/logged except credentials at issuance; raw refresh/reset/verification tokens are not stored server-side.
- Stable errors never reveal whether an email exists and never contain URLs, queries, solutions, prompts, provider output, SQL, or stacks.
- Additive response fields are allowed in v1; semantic/path/required-field breaks require v2.
- Extension sends contract/app version; backend supports the active Web Store rollout window.

## Deferred APIs

- Social/OIDC login.
- AI-chat, IDE, terminal, GitHub, or issue-tracker integrations.
- Split/merge/branch, export jobs, teams, sharing, billing, administration.
- Public API, webhooks, chat/completions, or generated debugging answers.
