# Render Environment Contract v1.1

ENG-100 defines the repository-side Render environment and secret contract for Trail v1.1. It documents the shape of the web service, database, health checks, runtime environment variables, secret ownership, and region assumptions without committing credentials or provider-generated URLs.

## Region contract

- Primary region: one Render region selected before staging creation.
- Region choice must keep the backend web service and PostgreSQL database co-located.
- Cross-region active-active, Kubernetes, and provider-specific application code are not part of v1.1.
- Region migration must preserve the portable OCI image, PostgreSQL/Flyway schema, environment-variable contract, and readiness health path.

## Web service contract

- Runtime: Docker image built from `apps/backend/Dockerfile`.
- Process type: one Render web service.
- Port: provided by Render through `PORT`; the backend listens on the platform port.
- Health check path: `/actuator/health/readiness`.
- Shutdown: SIGTERM with Spring Boot graceful shutdown.
- Background jobs: run in the web service for v1.1 with bounded PostgreSQL-backed leasing; a separate worker is deferred until measured contention.

## Database contract

- Database: Render PostgreSQL 17.x.
- Extension: pgvector 0.8.2 or explicitly reviewed compatible 0.8.x build.
- Connectivity: application traffic uses the Render internal/private database URL.
- External access: disabled or restricted to approved break-glass maintenance IPs.
- Backups: managed automated backups enabled before staging sign-off.
- Pooling: configured through Render managed pooling or backend Hikari settings sized to the selected plan.

## Runtime environment variables

| Variable | Owner | Required | Secret | Purpose |
|---|---|---:|---:|---|
| `PORT` | Render | Yes | No | Runtime HTTP port. |
| `TRAIL_DATABASE_URL` | Platform owner | Yes | Yes | Internal/private PostgreSQL JDBC URL. |
| `TRAIL_DATABASE_USERNAME` | Platform owner | Yes | Yes | Least-privilege database user. |
| `TRAIL_DATABASE_PASSWORD` | Platform owner | Yes | Yes | Database password injected by Render. |
| `TRAIL_JWT_KEY_ID` | Security owner | Yes | No | Identifier for the active RS256 signing key. |
| `TRAIL_JWT_ISSUER` | Security owner | Yes | No | Expected backend issuer. |
| `TRAIL_JWT_AUDIENCE` | Security owner | Yes | No | Expected extension audience. |
| `TRAIL_JWT_PUBLIC_KEY_PEM` | Security owner | Yes | No | Public verification key only. |
| `TRAIL_IDENTITY_EMAIL_PROVIDER` | Security owner | Yes | No | `local` for development only; `brevo` for staging/private beta/production. |
| `TRAIL_IDENTITY_EMAIL_FROM_ADDRESS` | Security owner | Conditional | No | Verified sender address used when Brevo is enabled. |
| `TRAIL_IDENTITY_EMAIL_FROM_NAME` | Security owner | No | No | Sender display name; defaults to `Trail`. |
| `TRAIL_IDENTITY_EMAIL_BREVO_API_KEY` | Security owner | Conditional | Yes | Brevo API key only when `TRAIL_IDENTITY_EMAIL_PROVIDER=brevo`. |
| `TRAIL_IDENTITY_EMAIL_BREVO_ENDPOINT` | Security owner | No | No | Defaults to Brevo's HTTPS transactional email API endpoint. |
| `TRAIL_IDENTITY_EMAIL_TIMEOUT` | Security owner | No | No | Bounded provider request timeout. |
| `TRAIL_IDENTITY_VERIFICATION_DESTINATION` | Security owner | Yes | No | Public HTTPS verification page URL for URL-carried email-verification tokens. |
| `TRAIL_IDENTITY_PASSWORD_RESET_DESTINATION` | Security owner | Yes | No | Public HTTPS password-reset page URL for URL-carried reset tokens. |
| `TRAIL_AI_PROVIDER` | AI owner | Yes | No | `mock` for staging unless production provider is explicitly approved. |
| `TRAIL_AI_OPENAI_API_KEY` | AI owner | Conditional | Yes | Provider key only when OpenAI adapter is enabled. |
| `TRAIL_EMBEDDING_PROVIDER` | AI owner | Yes | No | `mock` for staging unless production provider is explicitly approved. |
| `TRAIL_EMBEDDING_OPENAI_API_KEY` | AI owner | Conditional | Yes | Embedding provider key only when OpenAI embeddings are enabled. |
| `TRAIL_BACKGROUND_JOBS_SCHEDULER_ENABLED` | Platform owner | Yes | No | Enables bounded database-backed background job polling. |

## Secret rules

- Secrets are configured only in Render secret/environment management.
- Secrets are never committed, baked into Docker layers, copied into extension artifacts, or printed in logs.
- JWT private keys are not part of the repository-side contract and must not be committed.
- API keys are present only when the matching provider is enabled.
- Brevo local live delivery has been manually verified. Verified sender, production SPF/DKIM/DMARC/domain-authentication planning, and public verification/reset destinations are external release actions and must be complete before beta activation.
- Public health payloads and logs must not reveal secrets, database URLs, provider payloads, URLs, titles, or final solutions.

## No Kubernetes or provider lock in application code

Render is allowed only at the deployment configuration boundary. The backend remains a portable Spring Boot OCI image using environment variables, PostgreSQL, Flyway, and standard HTTP health endpoints. No Kubernetes manifests, Render SDK calls, provider-specific Java code, or provider-specific TypeScript runtime behavior are introduced by ENG-100.

## Deferred

ENG-100 does not create a Render service, provision PostgreSQL, deploy staging, run migrations in Render, configure DNS, or perform backup/restore. Those begin in ENG-101 and ENG-102.
