# Final Architecture Report — Trail v1.1

Date: 2026-07-05
Status: final production-readiness checkpoint
Supersedes: Trail architecture v1.0 readiness conclusions

## Executive decision

Trail remains the same product: a personal engineering memory that captures debugging context, stores the developer-verified solution, and recalls it when a similar problem appears. This refinement changes implementation choices and operating controls only.

## Every material change made

### Runtime and framework

- Kept Java 17 LTS and pinned Eclipse Temurin 17.0.19+10.
- Replaced Spring Boot 4.1.x with Spring Boot 3.5.15.
- Kept Maven and pinned Maven 3.9.16 through Maven Wrapper.

### Authentication

- Removed OIDC, PKCE, Google, GitHub, and Microsoft login from v1.1.
- Added email/password registration and sign-in.
- Added Argon2id password hashing, verified email, single-use hashed verification/reset tokens, generic anti-enumeration responses, password reset, short-lived asymmetric JWT access tokens, and rotated hashed opaque refresh tokens with reuse detection.
- Removed Chrome `identity` permission.

### AI and embeddings

- Formalized `AIProvider` for structured enrichment and independent `EmbeddingProvider` for vector generation.
- Business/application services depend only on these ports.
- Configuration selects provider adapters; v1.1 implements one production adapter plus Mock per port.
- Added structured explainability/provenance for provider, model, prompt version/hash, policy/schema, timestamp, embedding generation, retrieved sessions/ranks, evidence references, algorithm version, summary, and outcome.
- Raw provider requests/responses remain unstored and unlogged by default.

### Observability and operations

- Added Spring Boot Actuator, Micrometer, structured JSON logging, health, readiness, liveness, and private metrics.
- Explicitly deferred Grafana, Prometheus, OpenTelemetry, and distributed tracing.
- Added content/cardinality/privacy rules for every signal.

### Feature flags

- Added typed `FeatureFlagService` backed by validated configuration/environment values.
- Initial global flags: AI Suggestions, Ghost Replay, Semantic Search, and Experimental Features.
- No admin UI, user targeting, percentage rollout, or third-party flag platform.
- Security, privacy, authorization, sanitation, consent, and deletion cannot be disabled by flags.

### Deployment

- Selected Render for v1.1 Docker deployment.
- Selected one Render web service and Render Postgres over internal/private networking.
- Added deployment health-check, database access, pooling, backup/restore, and portability requirements.
- Kubernetes remains excluded.

### Package namespace

- Replaced the commercial-domain-dependent namespace with `io.github.aayush10016.trail`, a globally unique namespace tied to the project owner.
- Commercial domain control is not required before Java source is created.

### Database and API

- Added normalized email/password fields and hashed email-action tokens.
- Kept immutable user ID as ownership key.
- Kept embeddings in a separate model-versioned table.
- Expanded AI derivation/recommendation provenance without storing unrestricted provider payloads.
- Replaced pairing/OIDC endpoints with registration, verification, login, refresh, logout, and password-reset resources.
- Confirmed REST naming, `/api/v1`, POST-body sensitive search, cursor pagination, filter/page caps, ETag/If-Match, idempotency, RFC 9457, and strict validation.

### Engineering execution

- Replaced the 43 coarse issues with 106 independently assignable issues.
- Every issue is estimated at 4–8 focused hours and includes description, acceptance criteria, dependencies, labels, and effort.

## Decisions confirmed unchanged

- One polyglot monorepo.
- One capability-first Clean Architecture modular monolith.
- One Chrome Manifest V3 extension with popup, side panel, onboarding, and full-page app.
- No content scripts, Incognito, history import, or remote extension code.
- PostgreSQL 17.x and pgvector 0.8.2.
- Exact user-scoped vector retrieval before approximate indexes.
- PostgreSQL-backed explicit background jobs.
- IndexedDB for queue/drafts/cache and chrome.storage.local for small settings.
- TanStack Query for server state; Zustand only for ephemeral UI state.
- REST/OpenAPI contract and generated TypeScript client.
- Flyway forward-only expand/migrate/contract.
- GitHub Actions build-once artifact promotion.
- Final solution is user-authored and immutable from AI.
- AI failure cannot block capture, solution save, exact search, replay, or deletion.

## Complexity removed

- Spring Boot 4 migration/ecosystem risk.
- OIDC/PKCE/social-provider/pairing complexity.
- Chrome identity permission.
- Generic cloud-provider indecision for MVP.
- Direct Gemini or combined generation/embedding dependency.
- Generic package namespace uncertainty.
- Oversized multi-day GitHub issues.

Still excluded: Redis, Kafka, RabbitMQ, WebSockets, microservices, Kubernetes, graph database, dedicated vector/search service, content scripts, prompt CMS, feature-flag platform, full observability stack, and separate worker deployment.

## Deferred features and infrastructure

- Social/OIDC login.
- Additional AI/embedding provider adapters beyond selected production + Mock.
- AI-chat import, IDE/terminal capture, cross-browser support.
- Candidate split/merge, export, sharing, teams, billing, admin UI.
- User/cohort/percentage feature flags.
- Grafana, Prometheus, OpenTelemetry, distributed tracing.
- HNSW, Redis, partitioning, read replicas, separate workers, dedicated search/vector service, Kubernetes, multi-region.

Each remains evidence-gated and requires an explicit issue/ADR or product request.

## Database review conclusion

The schema is appropriately normalized for MVP:

- canonical user/session/artifact data is relational;
- bounded technology/error arrays avoid premature taxonomy tables;
- AI output/provenance uses versioned bounded structured fields where variable schemas are inherent;
- embeddings are isolated by model generation;
- composite ownership prevents cross-user relationships;
- raw capture retention limits growth;
- indexes follow actual user/time/status/candidate/search/job access patterns.

Accepted debt: recommendation retrieval/evidence may use bounded versioned JSON provenance rather than fully normalizing every ranked candidate/evidence item. If analytical querying or size becomes significant, migrate to child tables without changing the recommendation API.

## API review conclusion

All v1.1 endpoints follow a single convention. Authentication endpoints prevent enumeration and use strict throttles. Problem Session actions are modeled as resources/subresources. Sensitive searches use POST. Every list has stable cursor semantics and bounded filters/page size. Every error follows RFC 9457 without sensitive content. No black-box AI or provider-specific endpoint exists.

## Security review conclusion

The final design addresses:

- authentication abuse, verification/reset, JWT validation and refresh reuse;
- authorization and IDOR at repository/SQL boundaries;
- exact Chrome permissions and no content scripts;
- secret isolation and Render private database access;
- SQL injection, mass assignment, XSS/CSP, CSRF applicability, SSRF, prompt injection;
- OWASP Top 10 controls and release tests;
- content-free logs/metrics and private Actuator details;
- deletion of canonical, derived, vector, job, recommendation, and provenance data.

Remaining security risk is implementation quality, especially password flows, browser token storage, sanitizer parity, cross-user vector queries, and public management-endpoint exposure.

## Performance review conclusion

Expected first bottlenecks are raw capture writes/retention, PostgreSQL job contention, AI provider latency/cost, and vector/search query concurrency—not Java, Maven, or React.

Necessary v1.1 optimizations are already specified:

- batched idempotent capture;
- minimal raw-event indexes and chunked retention;
- bounded recent-window detection;
- one enrichment/embedding per meaningful fingerprint;
- exact per-user vector scans and lexical fallback;
- query/page/concurrency limits;
- connection pooling and job concurrency budgets;
- Render startup/heap/readiness benchmarks;
- no model calls in solution-save request path.

Further optimization is prohibited until benchmarks miss documented thresholds.

## Remaining risks

1. Developers may not provide final solutions consistently.
2. Browser metadata may confuse same-technology with same-problem.
3. Email/password ownership increases security and support burden.
4. Chrome storage cannot protect refresh credentials from a compromised profile.
5. AI/embedding provider terms, costs, and quotas remain vendor-selection risks.
6. Render PostgreSQL/pgvector version and access controls require deployment validation.
7. A future move to a company-owned namespace becomes noisy after public Java APIs are published.
8. One-region/one-database architecture has availability and scale ceilings.

## Technical debt explicitly accepted

- Single Spring Boot deployable and shared API/job resources.
- PostgreSQL jobs rather than a broker.
- No Redis/shared server cache.
- Exact vectors rather than HNSW.
- Global restart/redeploy feature flags.
- Render platform coupling at deployment configuration.
- One production AI and embedding provider each.
- No distributed tracing.
- Refresh credential stored in extension storage with scope/rotation mitigations.

All accepted debt has a trigger and migration path in the ADR set.

## Future migration paths

- Spring Boot 3.5 → 4: deprecation-free code, official migration, full integration/E2E, staged image.
- Email/password → social login: link verified provider identity to immutable user ID.
- Render → another cloud: portable OCI image, environment contract, PostgreSQL/Flyway, logical replication/restore, DNS cutover.
- PostgreSQL jobs → worker/broker: separate process first, then outbox/dual-publish/idempotent consumers.
- Exact pgvector → HNSW/dedicated search: filtered-recall benchmark, shadow/dual-write, measured cutover.
- Global flags → managed/runtime targeting: preserve `FeatureFlagService`, replace provider.
- Lightweight observability → managed metrics/tracing: retain Micrometer/SLF4J, add exporters after operational need.
- AI/embedding provider change: new adapter/model generation, shadow evaluation, backfill, atomic read switch, rollback window.

## Implementation gate

Implementation begins with ENG-001 and proceeds one issue at a time. Every implementation must cite applicable documents/ADRs, include applicable tests and self-review, preserve releasability, and update documentation only when behavior changes.

Trail Architecture v1.1 is frozen and ready for implementation.
