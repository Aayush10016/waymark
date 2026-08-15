# Security and Privacy

## Posture

Engineering problems can expose proprietary systems, internal URLs, source names, error details, credentials, vulnerabilities, and customer data. Trail treats metadata, final solutions, provenance, and embeddings as sensitive engineering content.

## Threat model

Primary risks:

- sensitive query parameters or private hosts captured before sanitation;
- cross-user Problem Session, recommendation, or vector-search leakage;
- credential stuffing, password-reset abuse, account enumeration, extension/session token theft;
- malicious page titles influencing models or rendering;
- model-provider retention/training or prompt injection;
- final solutions leaking through logs, analytics, support tools, or Actuator;
- employer use as developer surveillance;
- deletion that leaves embeddings, derivations, or recommendation provenance searchable.

## Data minimization

### Collected in MVP

- normalized email, Argon2id password hash, verification/session security metadata;
- sanitized top-level URL, hostname, bounded title, timestamp, transition/tab lineage;
- allowlisted search query;
- Problem Session title/description, optional short note, and final solution;
- derived summary, technologies, problem type, error phrases, embedding, and structured recommendation evidence;
- recommendation outcome events.

### Not collected

- raw passwords, raw refresh/reset/verification tokens;
- page body/DOM, screenshots, keystrokes, forms, cookies, headers, clipboard;
- source code, repository contents, terminal commands/output, IDE activity;
- raw AI conversation contents or unrestricted provider payloads;
- Incognito, localhost, private-network, local-file, iframe, or subresource activity;
- productivity or employee-performance signals.

## Authentication

- Spring Security email/password authentication; no OIDC/social login in v1.1.
- Normalize email for login uniqueness; ownership uses immutable user ID.
- Hash passwords with Argon2id parameters benchmarked on production-class hardware and versioned for rehash-on-login.
- Require verified email before server-side capture.
- Verification/reset tokens are high entropy, single-use, expiring, stored only as hashes, and rate-limited.
- Authentication/reset/verification responses do not reveal whether an account exists.
- Short-lived asymmetric JWT access tokens validate algorithm, issuer, audience, subject, session, expiry, and signing key ID.
- Opaque refresh credentials are hashed, rotated every use, and protected by family reuse detection/revocation.
- Password reset revokes existing refresh-session families.
- Raw credentials never enter URLs, logs, analytics, or persistence.
- Local development JWT signing keys are read at runtime from `$HOME\.trail\secrets\jwt-private.pem` and `$HOME\.trail\secrets\jwt-public.pem` by `scripts/dev/start-local-backend.ps1`. They are not generated on every restart and must not be committed, copied into `infra/docker/.env`, written through `setx`, logged, or included in test fixtures.

## Authorization

- Deny by default through Spring Security and application use-case checks.
- Repository methods require authenticated user ID plus resource ID.
- Search/vector/recommendation queries enforce user scope inside SQL.
- Composite ownership constraints reduce cross-user corruption.
- Never load by unscoped ID and check ownership afterward.
- Cross-user negative tests cover every endpoint and referenced resource.

## Extension controls

- Capture off until explicit consent.
- Pause and domain exclusion enforced before local persistence/upload.
- No Incognito, Chrome identity permission, or content scripts.
- Minimal named permissions, a narrow backend-only host permission for extension API fetches, and no remote executable code.
- Bounded queue cleared on sign-out only after warning about unsynced drafts.
- URL validation at capture and replay.
- Runtime-validated internal messages; no externally connectable messaging.

## API and web security

- Bearer access tokens use the `Authorization` header; refresh credentials are explicit request-body values, never cookies or URL parameters.
- The extension API does not use cookie authentication, so CSRF tokens are unnecessary; exact Origin/CORS allowlists and bearer validation remain mandatory.
- Extension host permissions must match the configured Trail backend API origin exactly. Local development uses `http://localhost:8080/*`; production must use an approved HTTPS API origin and must not request broad website host access.
- Parameterized JPA/SQL and allowlisted sort/filter fields prevent SQL injection; never concatenate user input into SQL/vector expressions.
- React renders titles, solutions, and AI output as text. No unsafe HTML, `eval`, or remote code; extension CSP remains restrictive.
- Explicit request DTOs reject mass assignment and enforce size/format constraints before domain handling.
- Rate limits cover registration, login, refresh, verification/reset, capture, search, and AI-triggering actions.
- Stable errors reveal no account existence, SQL, stack, URL, query, solution, or provider content.

## Identity email delivery

- `LOCAL` identity email delivery is development/test only. It records messages in memory and must not be used for staging/private beta/production sign-up flows.
- `BREVO` is the v1.1 production/private-beta transactional identity email provider selected by `TRAIL_IDENTITY_EMAIL_PROVIDER=brevo`.
- Required Brevo configuration is injected through environment variables only: `TRAIL_IDENTITY_EMAIL_BREVO_API_KEY`, `TRAIL_IDENTITY_EMAIL_FROM_ADDRESS`, optional `TRAIL_IDENTITY_EMAIL_FROM_NAME`, optional `TRAIL_IDENTITY_EMAIL_BREVO_ENDPOINT`, and optional `TRAIL_IDENTITY_EMAIL_TIMEOUT`.
- Brevo API keys are secrets. They must never be committed, logged, copied into extension artifacts, or exposed through health/error payloads.
- Local Brevo development uses `$HOME\.trail\secrets\brevo-api-key.txt` loaded only into the backend process environment by `scripts/dev/start-local-backend.ps1`. The verified sender address is non-secret local configuration and may be stored in `$HOME\.trail\config\local-backend.env`; the API key must not be stored there.
- Real Brevo delivery has been manually verified for the local backend and Gmail receipt path. Production custom-domain authentication work such as SPF, DKIM, and DMARC remains an external release action.
- Verification/reset email content remains generated by `IdentityEmailTemplateFactory`; provider adapters only deliver sender, recipient, subject, plain-text body, and HTML body required for transactional identity email.
- Verification and password-reset links carry high-entropy one-time tokens in the URL fragment (`#token=...`) so the initial page request does not send the token to the backend. The user-facing pages consume tokens from the URL, remove them from the address bar, and do not persist them in browser storage.
- Local development destinations default to `http://localhost:8080/verify-email` and `http://localhost:8080/reset-password`. Staging and production must override `TRAIL_IDENTITY_VERIFICATION_DESTINATION` and `TRAIL_IDENTITY_PASSWORD_RESET_DESTINATION` with reachable HTTPS URLs.
- Provider response bodies, request bodies, recipients, subjects, email bodies, verification tokens, password-reset tokens, and links are not logged.
- Brevo network failures, timeouts, 408, 429, and 5xx map to retryable `EmailDeliveryException`; ordinary 4xx provider rejections map to permanent failures. Application responses remain anti-enumeration generic.

## Backend and infrastructure controls

- Render PostgreSQL uses the internal/private URL, encryption, backups, least-privilege credentials, and restricted external access.
- Secrets arrive from platform secret/environment management, never repository, images, extension bundle, or logs.
- Production content is never copied to development; fixtures are synthetic.
- Dependency, container, workflow, and secret scans block defined severities.
- Actuator health details and metrics remain private/authenticated. Public health endpoints expose minimal status only.

## AI controls

- Business logic depends only on `AIProvider` and `EmbeddingProvider` ports.
- Provider terms must prohibit training on API data and define retention.
- Send bounded redacted fields for one declared derivation.
- Treat titles, queries, and user text as untrusted structured data, not instructions.
- Model receives no tools, network, secrets, database, or action permissions.
- Validate strict structured output and reject unsupported extraction.
- Never log prompts/responses by default.
- Embeddings receive the same deletion/access classification as source text.
- Persist provider/model/prompt/embedding/evidence provenance without unrestricted raw provider payloads.

## Final-solution invariant

- `final_solution` is user-authored.
- Model output cannot populate or silently edit it.
- Generated summary is separately labeled and stored.
- Product UI visually distinguishes the developer’s solution from AI-derived metadata.

## OWASP review

- Broken access control: user-scoped repositories, composite constraints, IDOR suite.
- Cryptographic failures: TLS, managed encryption, Argon2id, hashed tokens, managed secrets.
- Injection: parameterized SQL, strict DTOs, structured model input, escaped rendering.
- Insecure design: threat model, final-solution invariant, conservative recommendations and throttling.
- Security misconfiguration: typed startup config, exact CORS, private management endpoints, no debug exposure.
- Vulnerable components/integrity: pinned dependencies/actions/images, lockfiles, scans, immutable artifacts.
- Authentication failures: verification/reset controls, generic responses, rate limits, JWT validation, refresh rotation/reuse detection.
- Logging/monitoring failures: content-free structured logs, Micrometer health/metrics, canary leakage tests.
- SSRF: backend never fetches captured or replay URLs in v1.1.

## Logging and observability

Never log, tag metrics with, or send to generic analytics:

- URLs, titles, queries, errors, problem text, notes, final solutions, summaries;
- email, passwords, access/refresh/reset/verification tokens;
- model payloads/responses, retrieved text, or embeddings.

Use opaque IDs, counts, durations, low-cardinality states, stable codes, provider cost/latency, and recommendation outcome categories. Spring Boot Actuator, Micrometer, and structured JSON logs are the complete v1.1 observability stack; Grafana, Prometheus, OpenTelemetry, and distributed tracing are deferred.

## Deletion

- Session deletion immediately excludes source rows, search fields, derivations, embeddings, jobs, recommendations, and provenance from reads.
- Account deletion revokes sessions first, then purges user-owned active data.
- Encrypted backups age out under the published retention window.
- Content-free deletion evidence may remain.

## Product-use boundary

Trail does not expose passive personal sessions to employers, teams, or public profiles. Any future sharing requires explicit selection, sanitization preview, and a separate authorization model.

## Release gates

- Store permissions match packet-level behavior.
- URL/query sanitizer passes malicious and secret-bearing fixtures.
- Authentication abuse, reset, JWT, refresh reuse, and enumeration tests pass.
- Cross-user tests cover every endpoint/vector/recommendation query.
- Final-solution authorship and prompt-injection invariants pass.
- Provider retention/training terms are approved.
- Session/account deletion removes embeddings and active provenance.
- Actuator/metrics exposure is verified from the public network.
- No critical/high unresolved security finding for private beta scope.
