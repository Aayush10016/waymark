# Staging Deployment Verification v1

ENG-101 defines the staging verification evidence required after the ENG-100 Render contract is configured in Render.

## Owners

- Platform owner: creates Render service/database and records deployment evidence.
- Backend owner: verifies Flyway, health, logging, and metrics privacy.
- Security owner: reviews secret handling and actuator exposure.

## Required staging checks

| Check | Required evidence | Pass condition |
|---|---|---|
| Flyway migration | Deployment log or migration table evidence | All migrations apply exactly once and end at the current repository version. |
| Readiness | `GET /actuator/health/readiness` | HTTP 200 with minimal `UP` payload. |
| Liveness | `GET /actuator/health/liveness` | HTTP 200 with minimal `UP` payload. |
| Private metrics | `GET /actuator/metrics` from public network | Not exposed publicly. |
| Internal DB | Render service uses internal/private PostgreSQL URL | No public database URL is used by application traffic. |
| Log redaction | Startup, auth, capture, AI, and error logs | No JWTs, refresh tokens, passwords, API keys, database URLs, captured URLs, titles, prompts, or final solutions. |

## Local validation substitute

When Render credentials are unavailable, repository-side validation consists of:

- Maven verification with Testcontainers PostgreSQL 17/pgvector.
- Backend observability tests for minimal health payloads and private metrics.
- Structured log configuration checks.
- Docker image smoke test against `/actuator/health/readiness`.
- Render contract tests for internal database URL and secret management requirements.

This substitute does not claim that staging was deployed.

## Deferred

ENG-101 does not exercise backup/restore, rollback, production deployment, private-beta cohorts, or support controls.
