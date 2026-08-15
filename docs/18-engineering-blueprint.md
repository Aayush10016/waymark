# Trail Engineering Blueprint

Status: architecture candidate for kickoff
Version: 1.1
Owners: Technical Lead and module owners
Companion documents: [Product Requirements](01-product-requirements.md), [Experience Specification](17-experience-specification.md), [Engineering Milestones](19-engineering-milestones.md), [Ready for Development Report](20-ready-for-development-report.md)

## Executive decisions

- Eclipse Temurin 17.0.19+10, Java 17 source/bytecode, and Spring Boot 3.5.15.
- One Spring Boot modular monolith, one Chrome MV3 extension, one PostgreSQL 17 database with pgvector 0.8.2.
- Maven 3.9.16 through Maven Wrapper for Java; pnpm workspaces for TypeScript; root scripts orchestrate both without hiding native commands.
- Package by business capability, then Clean Architecture layer.
- REST/JSON under `/api/v1`; OpenAPI is the external contract.
- JWT access tokens plus opaque, rotated refresh credentials.
- PostgreSQL-backed jobs; no Redis, broker, WebSockets, Kubernetes, graph database, or separate search service.
- Render is the v1.1 deployment target: one Docker web service and managed PostgreSQL over private networking.
- Spring Boot Actuator, Micrometer, structured JSON logs, health/readiness/liveness, and private metrics are the complete v1.1 observability scope.
- AI is asynchronous and optional for solution durability. Lexical/error matching ships before proactive embedding recall.
- No content scripts in MVP. The `content/` extension boundary exists only as a prohibited/placeholder integration seam and contains no runtime entry.
- Trunk-based development with short-lived branches, squash merge, protected `main`, mandatory CI and review.

## 1. Repository structure

```text
trail/
├─ apps/
│  ├─ backend/
│  │  ├─ src/main/java/io/github/aayush10016/trail/
│  │  ├─ src/main/resources/
│  │  ├─ src/test/
│  │  ├─ pom.xml
│  │  └─ README.md
│  └─ extension/
│     ├─ src/
│     ├─ public/
│     ├─ tests/
│     ├─ manifest/
│     ├─ package.json
│     └─ README.md
├─ packages/
│  ├─ api-client/
│  ├─ ui/
│  ├─ extension-messages/
│  ├─ eslint-config/
│  └─ tsconfig/
├─ contracts/
│  ├─ openapi/
│  ├─ fixtures/
│  └─ README.md
├─ docs/
│  ├─ adr/
│  ├─ runbooks/
│  ├─ threat-model/
│  └─ numbered product/architecture documents
├─ infra/
│  ├─ docker/
│  ├─ environments/
│  └─ README.md
├─ scripts/
│  ├─ dev/
│  ├─ ci/
│  └─ release/
├─ .github/
│  ├─ ISSUE_TEMPLATE/
│  ├─ workflows/
│  ├─ CODEOWNERS
│  └─ PULL_REQUEST_TEMPLATE.md
├─ .editorconfig
├─ .gitignore
├─ package.json
├─ pnpm-workspace.yaml
├─ README.md
├─ SECURITY.md
└─ CONTRIBUTING.md
```

This is a target tree, not authorization to scaffold it before the foundation milestone is approved.

### Directory responsibilities

| Path | Responsibility | Boundary rule |
|---|---|---|
| `apps/backend` | Executable Spring Boot modular monolith | No frontend assets or generated TS |
| `apps/extension` | MV3 service worker, popup, side panel, onboarding, full-page app | No duplicated API types or domain policy |
| `packages/api-client` | Generated and wrapped TypeScript API client | Generated output is never hand-edited |
| `packages/ui` | Trail design tokens and reusable React primitives | Product features remain in extension app |
| `packages/extension-messages` | Runtime-validated internal extension message contracts | No Chrome API calls |
| `packages/eslint-config` | Shared lint policy | No product code |
| `packages/tsconfig` | Strict base TS configurations | Entry points extend rather than copy |
| `contracts/openapi` | Versioned API description | Contract changes require compatibility review |
| `contracts/fixtures` | Sanitized request/response and extension-message fixtures | No production/user data |
| `docs/adr` | One-way or expensive technical decisions | ADRs record context, decision, consequences |
| `docs/runbooks` | Restore, incident, deletion, provider outage, extension rollback | Must be executable by an engineer unfamiliar with the incident |
| `docs/threat-model` | Assets, trust boundaries, attack trees, mitigations | Updated when permissions/data flows change |
| `infra/docker` | Local PostgreSQL/pgvector and backend container definitions | Local-only at foundation; no premature platform framework |
| `infra/environments` | Environment inventory and deployment contracts | Secrets never committed |
| `scripts` | Thin, cross-platform developer/CI/release helpers | Scripts delegate to Maven/pnpm/Docker and fail loudly |
| `.github` | CI policy, ownership, contribution templates | Protected-branch behavior is documented here |

### Root tooling decisions

- Maven Wrapper is authoritative for backend builds.
- pnpm is authoritative for TypeScript workspace dependency management.
- Java is pinned during ENG-002; Node and the TypeScript toolchain are pinned during ENG-003.
- Root scripts provide discoverability (`verify`, `test`, `format`, `dev`) but do not replace native tool knowledge.
- Do not introduce Nx/Turborepo until TypeScript build graph time becomes a measured problem.
- Do not create a generic `shared/` directory. Shared code must have an owner and a named package; otherwise it becomes a dependency dumping ground.

## 2. Backend architecture

## Architectural style

Trail is a modular monolith organized by capability:

```text
io.github.aayush10016.trail
├─ bootstrap
├─ shared
├─ identity
├─ capture
├─ problem
├─ intelligence
├─ retrieval
└─ privacy
```

Each capability follows the same internal shape where needed:

```text
<capability>/
├─ domain/
│  ├─ model/
│  ├─ service/
│  ├─ event/
│  └─ port/
├─ application/
│  ├─ command/
│  ├─ query/
│  ├─ service/
│  └─ dto/
├─ adapter/
│  ├─ in/
│  │  ├─ web/
│  │  └─ job/
│  └─ out/
│     ├─ persistence/
│     ├─ ai/
│     └─ security/
└─ config/
```

Not every capability receives every directory. Empty architectural ceremony is prohibited.

### Dependency rule

Dependencies point inward:

```text
adapter → application → domain
config wires adapters to ports
domain depends only on Java and the tiny shared kernel
```

- Controllers call application use cases, never repositories.
- Application services own transactions and orchestration.
- Domain objects enforce lifecycle invariants.
- Persistence entities and Spring annotations stay in outbound adapters.
- Domain repository interfaces are outbound ports.
- Cross-capability communication uses application APIs or in-process domain events, not another module’s tables/repositories.

### Capability responsibilities

#### `bootstrap`

Application entry point, top-level Spring configuration, module wiring, health/readiness, and environment validation. It contains no business rules.

#### `shared`

Only stable primitives: typed IDs, clock interface, page/cursor types, problem-code abstraction, and domain-event marker. No `Utils`, generic base entity, generic repository, or shared business DTOs.

#### `identity`

Normalized email identity, Argon2id password authentication, email verification/reset, JWT access-token issuance/validation, opaque refresh-session rotation/reuse detection/revocation, and authenticated principal creation. Social login is deferred.

#### `capture`

Capture-batch validation, idempotency, URL/query server-side sanitation, consent enforcement, raw-event retention, resource classification, and current candidate input.

#### `problem`

Problem Session lifecycle, manual/automatic boundaries, artifact membership/order, candidate review, user-authored final-solution invariant, dismissal, and deletion command.

#### `intelligence`

Deterministic detection features, AI provider port, prompt registry, structured extraction, embeddings, derivation versioning, cost budgets, and PostgreSQL job handlers.

#### `retrieval`

Lexical search, exact error/technology evidence, vector candidates, hybrid ranking, recommendation threshold/suppression, explanation generation, and feedback.

#### `privacy`

Consent evidence, retention policy, account deletion orchestration, content-safe audit records, and policy/version reads. Local domain exclusions remain extension-owned in MVP.

### Conventional names mapped to Clean Architecture

| Conventional term | Trail location | Responsibility |
|---|---|---|
| `controller` | `adapter.in.web` | HTTP parsing, auth context, validation, response mapping |
| `service` | `application.service` or domain service | Use-case orchestration or pure domain policy; name specifically |
| `domain` | `domain.model/service/event` | Framework-free rules and state |
| `repository` | domain port + persistence adapter | Interface inward, implementation outward |
| `entity` | `adapter.out.persistence.entity` | Database mapping only; never API DTO |
| `dto` | `application.dto` and web request/response packages | Boundary-specific immutable data |
| `mapper` | next to owning adapter | Explicit mapping; manual by default |
| `security` | identity adapters/config | Authentication mechanisms and authorization integration |
| `config` | capability config/bootstrap | Dependency wiring |
| `exception` | domain/application errors mapped centrally | Stable problem codes, not control flow everywhere |
| `events` | domain event + application handlers | In-process decoupling after commit where required |
| `ai` | intelligence outbound adapter | Provider-specific API and schemas |
| `search` | retrieval capability | Search and recommendation only |
| `problem` | problem capability | Canonical Problem Session behavior |
| `session` | identity auth sessions; do not overload for Problem Sessions | Names must distinguish auth from engineering problem |

### Transaction boundaries

- One application command equals one primary transaction.
- Capture batch inserts and candidate updates may share a transaction only while bounded; expensive detection runs after insert on bounded data.
- Saving a final solution is synchronous and commits before any AI work.
- AI/job state is committed separately; provider network calls never hold database transactions open.
- Domain events that trigger jobs are recorded as `background_jobs` in the same transaction as the state change. A full generic outbox is unnecessary until external consumers exist.

### Error handling

- Domain/application errors use stable codes and safe metadata.
- One global HTTP exception mapper emits RFC 9457 Problem Details.
- Programmer errors become generic 500 responses with an opaque request ID.
- SQL/provider/stack details never cross the API.
- Retryability is explicit only for clients that can safely retry.

### Backend configuration

- Typed `@ConfigurationProperties`; startup fails for missing mandatory values.
- Profiles are limited to local, test, and deployed environment wiring; profiles do not encode business behavior.
- Secrets arrive through environment/workload secret injection.
- Feature flags use a typed `FeatureFlagService` backed by validated configuration/environment values. They are global per environment, have owner/removal criteria, and never disable security/privacy controls.

### Lightweight observability

- Spring Boot Actuator and Micrometer provide health and metrics instrumentation.
- Expose `/actuator/health`, `/actuator/health/liveness`, and `/actuator/health/readiness`; Render deployment health checks readiness.
- Expose `/actuator/metrics` only through an authenticated/private management boundary.
- Structured JSON logs use SLF4J/Logback and contain no browsing/problem/solution content.
- Minimal health/liveness/readiness may be reachable for the platform; detailed health and metrics remain private/authenticated.
- Grafana, Prometheus, OpenTelemetry, and distributed tracing are explicitly deferred.

## 3. Chrome extension architecture

## Target structure

```text
apps/extension/src/
├─ background/
│  ├─ listeners/
│  ├─ commands/
│  ├─ capture/
│  ├─ sync/
│  └─ index.ts
├─ content/
│  └─ README.md
├─ popup/
│  ├─ routes/
│  ├─ components/
│  └─ index.tsx
├─ sidepanel/
│  ├─ routes/
│  ├─ features/
│  └─ index.tsx
├─ app/
│  ├─ routes/
│  ├─ layouts/
│  └─ index.tsx
├─ onboarding/
│  └─ index.tsx
├─ features/
│  ├─ auth/
│  ├─ capture/
│  ├─ problem-session/
│  ├─ recall/
│  ├─ search/
│  ├─ timeline/
│  └─ settings/
├─ shared/
│  ├─ api/
│  ├─ chrome/
│  ├─ components/
│  ├─ errors/
│  └─ types/
├─ storage/
│  ├─ queue/
│  ├─ settings/
│  ├─ drafts/
│  └─ migrations/
├─ services/
│  ├─ messaging/
│  ├─ capture-policy/
│  ├─ replay/
│  └─ telemetry/
├─ hooks/
└─ test/
```

### Entry-point responsibilities

| Entry | Responsibility |
|---|---|
| `background` | Only long-lived coordinator: Chrome event listeners, local policy, queue, API sync, badge, commands |
| `content` | Intentionally empty in MVP; documents why page-context access is prohibited |
| `popup` | Status and immediate actions; no durable business state ownership |
| `sidepanel` | Recall, Ghost Replay, end-session solution capture, compact search |
| `app` | Full-page memory search/detail/timeline/settings |
| `onboarding` | Permission explanation, email registration/sign-in, consent, Demo Recall |
| `features` | Vertical React feature slices shared by surfaces |
| `storage` | Versioned local persistence adapters; no direct storage calls from components |
| `services` | Browser/API orchestration outside React |
| `hooks` | Thin React bindings; business rules remain in features/services |

### Message passing

Use one-time, JSON-serializable request/response messages for almost all communication. Long-lived ports are not correctness dependencies because MV3 workers and extension pages can unload.

Message envelope fields:

- schema version;
- message type;
- request/correlation ID;
- payload;
- source surface;
- optional safe error code in response.

Rules:

- Contracts live in `packages/extension-messages` and receive runtime validation.
- The service worker is the only recipient for privileged Chrome actions and authenticated sync.
- Popup/side panel/app query local state through typed messages or TanStack Query adapters.
- Never trust sender-provided tab IDs, URLs, or actions without validating sender context and payload.
- No `onMessageExternal` or `externally_connectable` in MVP.
- No content-script messages in MVP.
- Messages carry IDs and bounded state, not entire timelines or binary payloads.

### Event flow: navigation capture

```text
webNavigation event
→ background listener confirms top frame
→ local capture-enabled/exclusion check
→ URL/title/query sanitizer
→ durable local queue write
→ badge/candidate-local hint update
→ bounded batch sync
→ server accepted/duplicate/rejected response
→ remove only acknowledged queue records
→ notify open Trail surfaces that state is stale
```

### Event flow: solution capture

```text
server/local candidate becomes reviewable
→ background sets non-interruptive badge state
→ user opens side panel
→ user enters final solution
→ draft persists locally on each meaningful edit
→ API save request
→ local draft marked acknowledged, then removed
→ Problem Summary renders authoritative solution
→ asynchronous enrichment status is queried normally
```

### Event flow: Recall

```text
server returns strong current recommendation
→ background sets static ghost icon/badge
→ user invokes Recall
→ side panel requests recommendation detail
→ Ghost Replay renders exact prior solution
→ explicit resource-open message to background
→ background validates URL/count and opens tab
→ feedback API receives bounded outcome
```

### Local storage ownership

- `chrome.storage.local`: settings, consent/policy version, auth-session metadata, small state.
- IndexedDB: capture queue, final-solution drafts, bounded cached problem summaries/recommendations.
- No access token in React component state beyond the API adapter’s short-lived memory.
- Refresh credential storage is minimized and treated as device-compromise exposed; scope and rotation limit damage.
- Migrations are versioned, forward-only, fixture-tested, and never silently erase drafts.

### Extension failure policy

- Local sanitation failure drops the event and increments a content-free diagnostic; never upload raw fallback data.
- Queue failure pauses claimed recording state and informs the user.
- Network failure records locally and retries with bounded exponential backoff/jitter.
- Authentication failure preserves drafts/events until the disclosed cap and requests reconnect.
- Recommendation failure results in silence or manual lexical search, not a weak match.

## 4. Frontend architecture

## Stack roles

| Technology | Role | Misuse to avoid |
|---|---|---|
| React | Declarative extension surfaces | Business logic in component effects |
| TypeScript strict | Static contracts | `any`, unchecked message/API casts |
| Shadcn primitives | Accessible component starting points | Treating generated components as untouchable vendor code |
| Tailwind | Token-driven styling | Arbitrary one-off values and unreadable class duplication |
| TanStack Query | Server state, cache, mutations, invalidation | Copying API data into Zustand |
| Zustand | Ephemeral cross-component UI state only | Persistence, auth truth, server entity cache |
| React Router | Full-page/onboarding navigation and deep links | Forcing router complexity into tiny popup states |

### Frontend feature structure

```text
features/recall/
├─ api/
├─ components/
├─ hooks/
├─ model/
├─ routes/
├─ state/
├─ test/
└─ index.ts
```

- Feature public APIs are exported through `index.ts`.
- Cross-feature imports use public APIs, not deep internal paths.
- Pure formatting/matching view-model functions live in `model` and are unit-tested.
- API functions wrap the generated client with product-specific query keys and errors.
- Zustand stores are feature-local unless state genuinely crosses surfaces.

### State ownership

| State | Owner |
|---|---|
| Problem Sessions/recommendations/search results | TanStack Query |
| Capture enabled, queue/drafts | Extension storage via background service |
| Current dialog step, selected resource rows | Component or feature-local Zustand |
| Final-solution draft | IndexedDB adapter plus controlled form state |
| Authentication status | Background identity service, queried by UI |
| Toasts/panel layout | UI shell state |

### Query conventions

- Query keys are factories owned by each feature.
- Mutations update authoritative local draft first where offline durability is required.
- Invalidate narrowly by entity/current-state key, never the whole cache by default.
- Server errors map once into typed product errors.
- Offline/retry behavior is explicitly configured per query; destructive commands do not retry automatically.

### Routing

- Popup: explicit local state machine; no URL router required unless implementation proves simpler.
- Side panel: memory routing for Recall/end-session/search transitions; opening the panel never changes the webpage URL.
- Full-page app: browser router under extension URL for `/problems`, `/problems/:id`, `/search`, `/settings`, `/onboarding`.
- Deep links are internal extension routes with validated opaque IDs.
- Unknown routes go to safe Recall/search, not a generic blank 404.

### UI package policy

- `packages/ui` owns tokens, focus styles, Button, Field, Dialog, Card, Badge, Tooltip, Skeleton, ErrorNotice, and layout primitives.
- Domain components such as `GhostReplay`, `ProblemSolution`, and `TimelineRow` stay in extension features.
- Shadcn-derived source is reviewed and adapted to Trail tokens/accessibility.
- Storybook is deferred; component test pages and product screens are sufficient until UI reuse or visual regression volume justifies it.

### Frontend error boundary strategy

- One root boundary per surface.
- Feature boundaries around Recall and solution capture preserve the rest of the surface.
- Error boundaries report only opaque diagnostics.
- Draft preservation occurs below the UI boundary so a render crash does not discard user text.

## 5. Database review and target schema

## Review of the current design

The existing schema is directionally correct: user ownership is explicit, Problem Session is primary, artifacts use one polymorphic table, and raw events are short-lived. Four changes improve correctness and model evolution:

1. Move embeddings out of `problem_sessions` into `problem_embeddings`; embeddings have their own lifecycle/model/dimension.
2. Keep AI derivations canonical in `ai_derivations`; copy only current display/search projections into `problem_sessions` for fast reads.
3. Rename recommendation feedback to `recommendation_events`; shown/opened/dismissed/not-related/helpful are a state sequence, not only feedback.
4. Enforce same-user parent/child relationships with composite keys where practical, not only application checks.

### Target tables

#### `users`

- `id` UUID PK.
- `email_normalized` unique plus preserved display email.
- Argon2id `password_hash`, hash parameters/version, `email_verified_at`.
- `status`, failed-login/lock metadata, `created_at`, `updated_at`, `delete_requested_at`.
- Email is a login identifier, never a foreign/ownership key.

Indexes: unique normalized email; partial deletion-work/active lock index where query plans require it.

#### `auth_sessions`

- Session family ID, user ID.
- Hashed opaque refresh credential and rotation counter.
- Client type/version, created/last-used/expires/revoked/reuse-detected timestamps.

Indexes: user plus active expiry; unique current token hash. Raw refresh credentials never persist.

#### `email_action_tokens`

- Hashed random token, user ID, purpose (`VERIFY_EMAIL`, `RESET_PASSWORD`).
- Expiry, consumed, created timestamps and request metadata limited to abuse controls.

Indexes: unique active token hash and expiry cleanup. Raw tokens never persist.

#### `consent_grants`

- ID, user ID, policy version, capture/AI scope, granted/withdrawn timestamps.

Append-only evidence. No unnecessary long-term IP/user-agent retention.

#### `capture_events`

- ID, user ID, client event ID/sequence, client instance ID.
- Tab-session/optional parent event reference.
- Kind/resource/transition type.
- Sanitized canonical URL, keyed URL hash, hostname, bounded title, optional allowlisted query.
- Occurred/received time, quality flags.
- Candidate Problem Session ID, retention expiry, deleted timestamp.

Constraints/indexes:

- Unique `(user_id, client_event_id)`.
- B-tree `(user_id, occurred_at DESC)`.
- B-tree `(user_id, candidate_problem_session_id, occurred_at)` for assigned events.
- B-tree/BRIN on `retention_expires_at` selected after measured deletion plan.
- Partial active-row indexes; no GIN on arbitrary JSON.
- Partitioning deferred until row count/vacuum/retention cost proves need.

#### `problem_sessions`

- ID, user ID, status, source, version.
- User-confirmed/problem title and description.
- Exact user-authored final solution and `solution_authored_at`.
- One bounded optional note.
- Current generated summary projection.
- Current technologies/frameworks/error phrases as bounded arrays.
- Problem type.
- Start/end/duration, detector version, prompt/reminder state.
- Denormalized `search_document` text and `search_vector` tsvector.
- Created/updated/deleted timestamps.

Constraints/indexes:

- `SOLVED` requires nonblank `final_solution` and authored timestamp.
- B-tree `(user_id, status, updated_at DESC, id DESC)`.
- GIN `search_vector`.
- GIN array indexes only after query plans justify them; arrays are small and search document may suffice.
- Unique `(user_id, id)` supports composite child FKs.

Normalization decision: bounded technologies/frameworks/tags remain arrays in MVP. Separate taxonomy tables would create spelling/migration/UI complexity without a cross-user catalog requirement. Promote them only when user editing, analytics, or canonical technology identity becomes a real feature.

#### `session_artifacts`

- ID, user ID, Problem Session ID, stable position.
- Artifact type, sanitized URL/hash/hostname/title, optional query.
- Occurred time, source capture event ID, decisive flag/source, removed timestamp.

Constraints/indexes:

- Composite FK `(user_id, problem_session_id)` to Problem Session.
- Unique active `(problem_session_id, position)`.
- B-tree `(problem_session_id, position)`.
- B-tree `(user_id, hostname)` only if domain filtering/search is measured.

One table remains preferable to separate page/query/repo/video/chat tables because MVP behavior is uniform. Type-specific metadata belongs in explicit nullable columns only when a product flow uses it; avoid open-ended JSONB.

#### `ai_derivations`

- ID, user ID, Problem Session ID.
- Kind, prompt key/version/hash, provider/model/schema version.
- Input fingerprint, bounded structured output JSON, status.
- Token/cost/latency counters, safe failure code, timestamps.

Constraints: unique successful input/model/prompt tuple; composite ownership FK. Retain current plus bounded recent history.

#### `problem_embeddings`

- ID, user ID, Problem Session ID.
- Embedding model key/version, dimensions, input fingerprint.
- Fixed-dimension vector column for the selected model generation.
- Active/superseded timestamps.

Constraints/indexes:

- One active embedding per Problem Session/model generation.
- Composite ownership FK.
- B-tree `(user_id, model_version)`.
- Begin with exact user-scoped vector comparisons.
- Add HNSW only when measured p95/corpus size requires it; approximate filtering must be recall-benchmarked.

Dimension migration: a new vector dimension requires a new physical column/table generation and dual-read/backfill plan. Do not store dimension-ambiguous vectors in generic JSON/arrays.

#### `recommendation_events`

- ID, user ID, current candidate ID, recommended solved-session ID.
- Rank/evidence category, algorithm version.
- Shown/opened/helpful/not-related/dismissed timestamps.

No duplicated problem/solution content. Composite ownership validation applies to both session IDs.

#### `background_jobs`

- ID, user ID nullable only for global maintenance, aggregate ID.
- Type, state, attempts, next attempt, lease owner/expiry.
- ID-only payload, safe failure code, created/updated/completed timestamps.

Indexes: `(state, next_attempt_at)` partial for ready jobs; lease expiry; aggregate/type deduplication key.

#### `security_audit_events`

Add only for authentication, consent, deletion, and privileged support/security actions. Store opaque actor/target IDs, action/result/request ID/time; never content.

### Deliberately absent tables

- Journey graphs, branches, split/merge corrections.
- Generic notes/documents/tags taxonomy.
- Chat messages or prompt conversations.
- Teams/workspaces/sharing/billing.
- Search documents table until the single-row projection fails.
- Generic event sourcing log/outbox/processed-messages.
- Export/object-storage jobs in MVP.

### Scalability review

- Raw capture events dominate writes; keep indexes minimal and deletion chunked.
- Solved-session/vector count is orders of magnitude smaller; optimize correctness first.
- User-scoped exact vector scan is appropriate for early personal corpora.
- If HNSW is introduced, filtered approximate recall requires explicit benchmark/overfetch; an index existing does not prove correct tenant-filtered recall.
- Connection pooling is bounded below database limits; background jobs have separate concurrency budgets.
- No database partitioning, read replicas, or sharding before measured maintenance/query failure.

### Migration standards

- Flyway forward-only migrations.
- One concern per migration; immutable after shared/deployed.
- Expand/backfill/contract for breaking changes.
- Data backfills are resumable jobs when they can exceed deployment time.
- Every schema change includes compatibility, rollback-by-deploy, data-retention, and index-lock review.
- CI applies migrations from empty and previous-release snapshots using PostgreSQL with pgvector.

## 6. API design review and target contract

## Conventions

- Base path `/api/v1`.
- Resource names are plural kebab-case nouns.
- JSON uses camelCase; enums use uppercase snake case.
- IDs are opaque UUID strings.
- RFC 3339 UTC timestamps.
- RFC 9457 Problem Details with stable `code` and `requestId` extensions.
- Cursor pagination; no offset for mutable/large collections.
- `Idempotency-Key` on registration, capture batches, solution save, and destructive/retryable commands where duplicates matter.
- Optimistic writes use `If-Match` entity version/ETag.
- Sensitive search strings are sent in POST bodies and excluded from access logs.

### Authentication API

| Method | Path | Purpose |
|---|---|---|
| `POST` | `/api/v1/registrations` | Create unverified email/password account |
| `POST` | `/api/v1/email-verification-requests` | Request another verification email with generic response |
| `POST` | `/api/v1/email-verifications` | Consume single-use verification token |
| `POST` | `/api/v1/auth-sessions` | Authenticate and issue JWT access/opaque refresh credentials |
| `POST` | `/api/v1/auth-sessions/refresh` | Rotate opaque refresh credential and issue JWT access token |
| `DELETE` | `/api/v1/auth-sessions/current` | Revoke current extension session |
| `POST` | `/api/v1/password-reset-requests` | Generic reset request without enumeration |
| `POST` | `/api/v1/password-resets` | Consume reset token and revoke sessions |

Social/OIDC endpoints do not exist in v1.1. Authentication endpoints use strict rate limits and generic account-existence responses.

### Capture API

| Method | Path | Purpose |
|---|---|---|
| `POST` | `/api/v1/capture-batches` | Upload bounded sanitized events with per-event result |
| `GET` | `/api/v1/capture-state` | Current candidate, prompt eligibility, recommendation summary, policy freshness |

`POST /capture-batches` returns 200 for a processed per-event result, not 202 unless the whole batch is merely queued. Accepted/duplicate/rejected arrays make partial success explicit.

### Problem Session API

| Method | Path | Purpose |
|---|---|---|
| `POST` | `/api/v1/problem-sessions` | Manually start a candidate |
| `GET` | `/api/v1/problem-sessions` | Cursor list; default solved/recent |
| `GET` | `/api/v1/problem-sessions/{id}` | Detail and ordered artifacts |
| `PATCH` | `/api/v1/problem-sessions/{id}` | Edit title/description/note/status-safe fields and artifact removal commands defined by contract |
| `DELETE` | `/api/v1/problem-sessions/{id}` | Delete source and derived session data |
| `POST` | `/api/v1/problem-sessions/{id}/finish` | Close manual/active capture boundary |
| `PUT` | `/api/v1/problem-sessions/{id}/solution` | Idempotently set/update exact user-authored solution and solved state |
| `DELETE` | `/api/v1/problem-sessions/{id}/solution` | Reopen/remove solved outcome only if product permits; defer unless required |
| `POST` | `/api/v1/problem-sessions/{id}/deferrals` | Record the single allowed prompt deferral |
| `GET` | `/api/v1/problem-sessions/{id}/recommendation` | Top eligible prior solution for current candidate, or 204 |

Use action subresources only for real state transitions that do not map cleanly to CRUD. Avoid colon-action naming mixed with noun resources.

Artifact removal should become `DELETE /problem-sessions/{id}/artifacts/{artifactId}` if independent removal is needed beyond candidate patch. Do not accept an ambiguous free-form patch operation list.

### Search API

| Method | Path | Purpose |
|---|---|---|
| `POST` | `/api/v1/problem-session-searches` | Search only solved Problem Sessions with query/filter/cursor body |

The endpoint models a non-persisted search command because the query is sensitive and complex. Response contains results, match evidence, opaque next cursor, and degradation mode (`HYBRID` or `LEXICAL`). It never returns raw model/vector scores.

### Recommendation feedback

| Method | Path | Purpose |
|---|---|---|
| `POST` | `/api/v1/recommendations/{id}/events` | Append one allowlisted outcome: opened/helpful/not-related/dismissed |

The server derives `shown`; clients cannot submit arbitrary metrics or another user’s session IDs.

### Privacy API

| Method | Path | Purpose |
|---|---|---|
| `GET` | `/api/v1/privacy-policy` | Current machine-readable policy/version |
| `POST` | `/api/v1/consent-grants` | Record explicit consent |
| `POST` | `/api/v1/consent-withdrawals` | Withdraw and stop server processing |
| `DELETE` | `/api/v1/account` | Reauthenticate/revoke and begin deletion |
| `GET` | `/api/v1/account-deletion` | Deletion status if required after request |

Local domain exclusions have no API in MVP.

### Pagination and filtering

- Cursor encodes stable sort key, ID, filter fingerprint, and expiry; it is signed/opaque.
- Default list sort: `updatedAt DESC, id DESC`.
- Search sort: relevance then stable ID; cursor pins algorithm/index version for one traversal.
- Allowlisted filters only: status, created date range, technology, problem type.
- Maximum page size 50; default 20.
- Unknown filters/fields are rejected, not ignored silently on writes.

### Problem response example shape

Every error includes:

- `type` URI;
- human `title`;
- HTTP `status`;
- stable machine `code`;
- opaque `requestId`;
- safe `errors` keyed by field when validation fails.

No stack, SQL, URL, query, solution, prompt, provider response, or authorization existence leak.

### Compatibility

- Additive response fields are allowed within v1; clients ignore unknown fields.
- Request schemas reject unknown fields for security-sensitive commands after client compatibility is proven.
- Breaking semantics/path/required fields require v2.
- Extension sends app/API contract version; backend supports the active Web Store rollout window.
- Deprecation is telemetry-backed and security exceptions are documented.

## 7. AI module review

## Simplified delivery sequence

Embeddings should wait until the non-AI memory loop works:

1. deterministic candidate detection;
2. user-authored solution capture;
3. exact lexical/error/technology search;
4. structured extraction if it improves search/scanability;
5. solved-session embeddings;
6. current-session embedding and proactive hybrid Recall.

This is sequencing, not removal. Semantic similarity is required before the killer feature can be considered validated, but it should not block early capture/solution milestones.

### Module ports

- `AIProvider`: structured summary/technologies/problem type/error phrases; selected by `trail.ai.provider`.
- `EmbeddingProvider`: vector for a canonical bounded document; selected independently by `trail.embedding.provider`.
- `SimilarityEngine`: combines repository candidates and evidence; provider-independent.
- `RedactionPolicy`: produces provider-safe input or refuses processing.
- `PromptRegistry`: resolves immutable prompt definition by key/version/hash.
- `AiBudgetPolicy`: per-job/global limits and degradation choice.

Provider-specific SDK types never leave the outbound adapter.

Production implements one `AIProvider` and one `EmbeddingProvider` plus mock adapters. Gemini, OpenAI, Ollama, and local adapters are possible implementations, not business dependencies and not all MVP deliverables.

### Prompt versioning

- Prompt templates live as reviewed backend resources, not database-editable production text.
- Identity is `promptKey + semanticVersion + contentHash + outputSchemaVersion`.
- Any wording, examples, schema, redaction, or model change creates a new version.
- `ai_derivations` records exact prompt/model/schema/input fingerprint.
- Tests include golden structured fixtures, adversarial titles, insufficient context, and schema failures.
- Rollout is feature-flagged by server config with owner/expiry and supports rollback to a prior version.
- Do not build a prompt CMS or dynamic admin editor in MVP.

### Explainability and provenance

- Every AI derivation records provider, model, prompt key/version/hash, output schema, policy version, timestamp, input fingerprint, status, generated summary, and embedding generation where relevant.
- Every recommendation records retrieved solved-session IDs/ranks, evidence codes, referenced prior artifact/session IDs, algorithm version, and resulting user-visible explanation.
- User-facing `Why this?` copy is generated deterministically from evidence codes, never by a model.
- Raw provider requests/responses are not stored or logged by default.

### Provider input

Only bounded redacted metadata and explicit user text needed for the derivation. Full URLs become host/resource labels; secrets, emails, UUID-like tokens, private paths/hosts, and sensitive parameters are stripped. Page bodies, code, cookies, AI conversation contents, and hidden DOM never enter.

### AI failure behavior

- Final-solution save commits before AI.
- Enrichment timeout/failure leaves deterministic title/search intact.
- Embedding failure yields lexical/error search and no weak proactive recommendation.
- Invalid structured output is rejected, not partially trusted.
- Retry only transient failures with capped exponential backoff.
- Permanent policy/validation failures become terminal safe codes.
- UI says the solution is saved and matching details are delayed/unavailable.

### Cost controls

- Never call a model per page.
- One enrichment and solved-session embedding per meaningful input fingerprint.
- Current-session embedding only after detection threshold and meaningful context change, with minimum interval.
- Use the smallest model that passes the evaluation corpus.
- Cache exact derivations by fingerprint/model/prompt under user-safe database keys.
- Bound artifact count, title/query lengths, output schema, retries, concurrency, and daily spend.
- Track cost per helpful Recall, not per event/session.
- No generative reranker or generated match explanation.

### Quality gates

- Structured extraction groundedness and correction rate.
- Positive/hard-negative pair precision.
- Recommendation precision is the launch gate; Recall@K alone is insufficient.
- Same technology/different problem is a mandatory hard-negative cohort.
- Uncalibrated vector scores never become percentages in UI.

## 8. Security review

## Authentication

- Normalize email for uniqueness while preserving display form; never key ownership by email.
- Hash passwords with Spring Security Argon2id parameters benchmarked for production hardware and versioned for rehash-on-login.
- Require verified email before server-side capture; verification/reset tokens are random, single-use, expiring, and stored only as hashes.
- JWT access token lifetime approximately 5–10 minutes; asymmetric signing, pinned algorithm, issuer/audience/subject/session/expiry validation, key rotation by `kid`.
- Opaque refresh credentials stored hashed server-side, rotated every use, family reuse detection and revocation.
- Generic registration/reset/verification responses prevent account enumeration; apply IP/account/device throttling and breached-password checks where feasible.
- Separate extension audience/scope; no indefinite stateless JWT.
- Reauthentication for account deletion and sensitive identity/password changes.

### Authorization

- Deny by default in Spring Security.
- Application use cases receive authenticated `UserId`; repositories require it in every user-data method.
- Composite database ownership FKs reduce cross-tenant corruption.
- Never `findById` then check ownership after loading; query by user and ID.
- Search/vector/feedback/bulk operations validate every referenced row belongs to one user.
- Cross-user negative tests cover every endpoint.
- PostgreSQL RLS is deferred defense-in-depth, not a substitute for correct application scoping.

### Extension permissions

MVP expected permissions: storage, alarms, tabs, webNavigation, and sidePanel. Email/password authentication requires no Chrome identity permission. Exact permission warnings are confirmed in ENG-008.

Explicitly absent: history import, cookies, downloads, clipboard, debugger, scripting/content scripts, Incognito, externally connectable messaging, native messaging.

### Sensitive data handling

- Sanitize before local queue and repeat at ingestion.
- Block private/local/prohibited hosts and credential-bearing URLs.
- Remove fragments, trackers, and sensitive query parameters.
- Search queries only from versioned allowlisted engines.
- Content-free logs, traces, metrics, crash reports, analytics, and support diagnostics.
- Production data never copied to development; fixtures are synthetic.
- Database/storage/backups use managed encryption; TLS for all external/store connections.
- Application-layer encryption is deferred until a query/search/key-management design proves net value; do not claim E2EE.

### Secrets

- Managed secret store/workload identity in deployed environments.
- Local `.env` files ignored; committed `.env.example` contains names only.
- No secrets in extension bundle, repository, image layers, CI logs, test fixtures, or error messages.
- AI, email delivery, JWT signing, database, and deployment credentials have independent scope/rotation.
- Automated secret scanning and dependency review block merges.

### Prompt injection

- All captured/user strings are untrusted data inside structured fields.
- Model has no tools, network, code execution, secrets, or database access.
- System/developer prompt is immutable and separates instructions from data.
- Output is strict-schema validated, length bounded, escaped on render, and cannot trigger commands.
- Final solution cannot be populated from model output.
- Adversarial page titles such as instruction overrides are fixtures in AI and UI tests.

### OWASP Top 10 mapping

| Risk | Trail controls | Verification |
|---|---|---|
| Broken access control | User-scoped repositories, composite FKs, deny default | Endpoint IDOR suite |
| Cryptographic failures | TLS, managed at-rest encryption, hashed refresh tokens, secret manager | Config/security review |
| Injection | Prepared ORM/SQL, strict schemas, escaped UI, structured AI | SAST and malicious fixtures |
| Insecure design | Threat model, abuse cases, final-solution invariant, rate limits | ADR/threat review |
| Security misconfiguration | Typed startup config, hardened headers/CORS, no debug endpoints | Deployment tests |
| Vulnerable components | Lockfiles, Dependabot/renovation policy, SBOM/scanning | CI gates |
| Authentication failures | Argon2id, verified email, generic reset responses, throttling, refresh rotation/reuse detection, scoped JWTs | Auth integration tests |
| Software/data integrity failures | Protected CI, pinned dependencies/images, signed releases later | Provenance review |
| Logging/monitoring failures | Content-free security events, anomaly/error alerts | Canary leakage tests |
| SSRF | Backend never fetches captured URLs in MVP | Architecture test/review |

### Additional attack vectors

- **Malicious extension messages:** validate schema and sender; privileged actions only in worker.
- **Queue poisoning/replay:** client event uniqueness, bounds, auth scope, server sanitation.
- **Mass assignment:** explicit request DTOs; never bind persistence entities.
- **JWT confusion:** fixed algorithm/audience/issuer; separate access/refresh mechanics.
- **Vector leakage:** same sensitivity/deletion as source and SQL user filter.
- **Recommendation oracle:** generic not-found behavior and ownership before similarity detail.
- **XSS via title/solution:** text rendering only, CSP, no unsafe HTML/eval.
- **Dependency/supply chain:** minimal dependencies, lockfiles, review updates, Web Store build provenance.
- **Surveillance misuse:** no team/admin APIs or productivity metrics; policy and product boundary enforced.

### Security release gates

- Updated threat model and data-flow diagram.
- Permission disclosure matches packet capture.
- IDOR suite and token rotation/reuse tests pass.
- Secret/canary strings absent from logs and AI payloads.
- Deletion removes active source/search/vector/derivation records.
- No critical/high known vulnerability without explicit time-bounded acceptance.

## 9. Testing strategy

## Test pyramid and ownership

Tests live beside the behavior they protect. A separate QA phase is not the quality strategy; engineers own automated coverage and the Product/Design acceptance checklist.

### Backend unit tests

Tools: JUnit 5, AssertJ, Mockito only at true ports, property-based testing where invariants benefit.

Targets:

- Problem Session state transitions and final-solution invariant.
- URL/query sanitation pure functions.
- detection feature scoring/boundaries.
- hybrid rank/evidence/threshold/suppression.
- prompt redaction and structured output validation.
- cursor/idempotency/token policies.

Rules: no Spring context for domain/application unit tests; fixed test clock and deterministic IDs.

### Backend integration tests

Tools: Spring Boot test slices/full context where appropriate, Testcontainers PostgreSQL image with pgvector, and controlled HTTP fakes for email/AI boundaries.

Targets:

- Flyway from empty and previous-release fixture.
- Repository ownership and composite FK behavior.
- PostgreSQL full-text/vector queries and explain-plan guardrails.
- capture batch idempotency/partial result.
- authentication refresh rotation/reuse.
- transaction-to-background-job atomicity and job lease/retry.
- deletion across canonical/derived/search/vector records.

Never replace PostgreSQL/pgvector integration tests with H2.

### API contract tests

- OpenAPI schema lint and breaking-change check.
- Backend responses validated against contract.
- Generated TypeScript client compiles against extension.
- Sanitized fixtures cover success, validation, conflict, auth, rate limit, and degradation.
- Consumer smoke tests protect the currently distributed extension contract.

### Frontend tests

Tools: Vitest, React Testing Library, user-event, MSW-style API boundary.

Targets:

- Feature view models and query keys.
- End Session draft/persistence/recovery.
- Similar Problem evidence and no-match behavior.
- Ghost Replay explicit tab actions and feedback.
- loading/empty/success/error/offline/AI states from the experience spec.
- keyboard/focus/reduced-motion/accessibility checks.

Test behavior and accessible output, not component implementation/class names.

### Extension tests

- Unit test Chrome adapters with narrow typed fakes, not a giant global mock.
- Queue durability, migration, cap, retry, and ack semantics.
- Message schema/sender validation and unknown-version rejection.
- Service worker suspension between lifecycle steps.
- Permission removal and auth expiry.
- badge/popup/side-panel state priority.
- replay URL/count validation.

### End-to-end tests

Tools: Playwright with a persistent Chromium context and unpacked extension; deployed/local backend plus real Testcontainers database.

Critical journeys:

1. install → permission/consent → recording ready;
2. capture technical navigation → candidate → finish → solution save;
3. offline final-solution draft → restart → reconnect/sync;
4. solved memory → similar current candidate → Recall → Ghost Replay → resource open/helpful;
5. not-related/dismiss suppression;
6. delete Problem Session/account and verify no reads;
7. permission revoked and restored;
8. AI outage with deterministic fallback.

Do not automate every animation pixel. Assert state order, focus, reduced motion, and critical visual snapshots only.

### Security and privacy tests

- IDOR matrix for all endpoints/resources.
- malicious URLs/titles/query parameters and Unicode/IDN hosts.
- JWT issuer/audience/algorithm/expiry/reuse cases.
- CSRF/CORS/origin behavior for any browser-mediated web flow.
- prompt-injection fixtures and unsupported model output.
- log/trace canaries proving sensitive fields do not escape.
- dependency/container/secret/SAST scans.
- packet inspection against extension disclosure before release.

### Performance tests

- reconnect/capture batch burst and duplicates.
- raw-event cleanup under realistic table size.
- per-user exact vector retrieval with skewed histories.
- HNSW comparison only if the index trigger is reached.
- candidate detection bounded-window cost.
- extension queue and library behavior at documented caps.

### Coverage policy

Coverage is a floor, not a quality score:

| Scope | Line target | Branch target |
|---|---:|---:|
| Backend domain/application | 85% | 80% |
| Security, sanitation, solution lifecycle | 95% | 90% |
| Backend adapters overall | 70% | 60% |
| Frontend feature/model logic | 80% | 75% |
| UI presentational components | 60% | 50% |
| Extension storage/messaging/capture | 85% | 80% |

Changed-lines coverage target: 85%, with documented exceptions for generated code, configuration, and trivial adapters. Exclude generated API clients and migrations from percentage gaming; test them through contract/integration behavior.

### CI stages

1. format/lint/type/static checks;
2. unit tests in parallel;
3. backend integration/Testcontainers;
4. contract generation and compatibility;
5. frontend/extension tests;
6. build backend image and extension artifact;
7. security/dependency/secret scans;
8. critical E2E on protected branches/nightly until runtime permits per PR.

## 10. Development standards

## Naming

### Product language

- `ProblemSession` is the canonical entity; never `Journey` or `RabbitHole` in new code.
- `AuthSession` always includes `Auth` to avoid confusion.
- `finalSolution` means exact developer-authored text.
- `generatedSummary` means AI-derived text.
- `Recommendation` is a prior solved-session match; `Recall` is the user interaction; `GhostReplay` is the UI experience.

### Java

- Packages lowercase by capability/layer.
- Types nouns; use cases explicit verbs/nouns: `SaveProblemSolution`, `FindProblemRecommendation`.
- Interfaces are named by capability, not `I*` or `*Impl`.
- Persistence types end `JpaEntity` only where disambiguation helps; repositories specify adapter intent.
- Tests use `should...when...` or behavior sentences consistently.

### TypeScript/React

- Files kebab-case; React component exports PascalCase; hooks `useX`; event handlers `handleX`; props `onX`.
- Booleans read as predicates (`isOffline`, `canReplay`).
- Message types are namespaced verbs/nouns (`capture.getState`, `replay.openResource`).
- Avoid generic `data`, `item`, `manager`, `helper`, `utils`; use domain meaning.

### Database/API

- SQL snake_case, singular table? Trail chooses plural table names consistently as specified.
- API paths plural kebab-case; JSON camelCase; enum uppercase snake case.
- Stable error codes namespace by capability (`PROBLEM_INVALID_STATE`, `CAPTURE_EVENT_REJECTED`).

## Java standards

- Java 17 language baseline even if Spring Boot runtime supports newer; upgrades require ADR.
- Constructor injection only; no field injection.
- Immutable records for boundary DTOs where appropriate.
- Domain model avoids Spring/JPA/Jackson annotations.
- No Lombok in the foundation; generated convenience does not justify hidden semantics.
- No generic base entity/repository/service/controller.
- `Optional` for return values, not fields/parameters/entities.
- Checked exceptions only for truly recoverable boundary contracts; domain failures are typed results/exceptions mapped once.
- `@Transactional` on application use cases, not controllers; read-only queries declared where useful.
- Logging is parameterized and content-safe; no catch-and-log-and-rethrow duplication.
- Nullness/static analysis policy selected and documented in ENG-001.
- Format and import order automated; warnings fail CI according to pinned rules.

## TypeScript standards

- `strict` and `noUncheckedIndexedAccess`; no implicit `any` and no `as unknown as` escapes without justification.
- Runtime validate extension messages, external storage data, and non-generated external boundaries.
- Generated OpenAPI types are authoritative; do not recreate API interfaces manually.
- Prefer discriminated unions for UI/message states.
- Components are pure; side effects live in hooks/services.
- TanStack Query owns server data; Zustand owns ephemeral UI state.
- No direct Chrome API calls from feature components.
- No unsafe HTML rendering/eval/remote code.
- Accessibility roles/names are part of test contracts.
- Tailwind uses shared tokens/component variants; arbitrary values require design-system review.

## Documentation standards

- README in every deployable app/package: purpose, owners, dependencies, local verify commands, failure modes.
- ADR for expensive/one-way decisions; do not use ADRs for routine implementation.
- OpenAPI descriptions explain semantics/privacy, not only types.
- Flyway migrations include intent in filename/comment and companion operational note for risky changes.
- Public domain/application types have concise Javadoc when invariants are not obvious.
- Runbooks use prerequisites, detection, immediate mitigation, recovery, verification, escalation.
- Mermaid diagrams are source-controlled text and updated with boundary changes.
- No customer/user data in screenshots, examples, or fixtures.

## Branch and commit strategy

- Protected `main` is always releasable.
- Short branches: `feat/123-recall-card`, `fix/456-refresh-reuse`, `chore/789-flyway-check`.
- Target lifetime under two working days; split work behind server-side flags when necessary.
- No GitFlow/develop/release branches.
- Conventional Commits encouraged on individual commits; PR title must follow Conventional Commit format.
- Squash merge produces one intentional mainline commit linked to issue.
- Rebase/update before merge; no merge commits from main into feature branches.
- Emergency fixes use the same PR/check process with expedited reviewers, then post-incident review if warranted.

## Pull request standards

- One outcome, linked issue, explicit non-goals.
- Prefer under 400 changed hand-written lines; larger changes explain why and offer review sequence.
- Include tests, security/privacy impact, schema/API compatibility, UI evidence, rollout/rollback.
- At least one approval; two for auth, capture permissions, deletion, AI data policy, schema breaking changes, or release workflows.
- Author resolves comments; reviewer who requested a change confirms it when material.
- No self-merge unless incident policy explicitly allows.

## Definition of Done

- Acceptance criteria pass.
- Tests at appropriate levels and CI green.
- API/schema/message compatibility reviewed.
- Security/privacy and logging classification reviewed.
- UX states/accessibility match specification where applicable.
- Metrics/diagnostics are content-safe and purposeful.
- Documentation/ADR/runbook updated.
- Feature flag owner/removal date recorded if used.
- Rollback or disable path documented.

## 11. GitHub milestone model

The executable issue backlog is maintained in [Engineering Milestones](19-engineering-milestones.md). Rules:

- Every issue fits 4–24 focused engineering hours; split larger work.
- Estimates include tests, docs, and review fixes.
- Dependencies use issue IDs, not implicit milestone order.
- Difficulty is `S`, `M`, `L`, or `XL`; `XL` must be split before assignment unless it is an explicit research spike.
- Labels use `area:*`, `type:*`, `risk:*`, and `priority:*` namespaces.
- A single engineer executes milestones sequentially in roughly 16 weeks; five engineers may parallelize only where dependencies permit.

## 12. Critical design review

## Unnecessary complexity to resist

### Clean Architecture theater

Risk: every CRUD path gains command, handler, port, adapter, mapper, entity, event, and DTO regardless of value.

Decision: capability boundaries are mandatory; internal layers are created only when they isolate domain policy or an external boundary. Simple read queries may use a dedicated application query adapter without reconstructing rich aggregates.

### Shared-package sprawl

Risk: `shared` and `packages/ui` become dumping grounds that couple all teams.

Decision: shared kernel remains tiny; domain components stay feature-owned; new shared exports need two real consumers and an owner.

### Premature event architecture

Risk: generic outbox, event bus, event sourcing, and Redis appear before external consumers.

Decision: transactionally insert explicit `background_jobs` for known async work. Introduce an outbox only for an actual external delivery boundary.

### AI framework abstraction

Risk: elaborate provider orchestration, prompt CMS, agents, chains, and model routing hide a two-call product need.

Decision: two narrow provider ports—structured enrichment and embeddings—plus plain prompt resources and schemas.

## Hidden technical debt

### Extension authentication storage

Chrome storage is not a secure enclave. A compromised profile can steal refresh material.

Mitigation: narrow extension scope, short access tokens, rotated opaque refresh credentials, reuse detection, remote revoke, no broader account privileges.

### Candidate boundary corrections

Removing split/merge makes false grouping cheaper to implement but can frustrate users.

Mitigation: manual start/finish and dismiss/restart. Add split/merge only if measured candidate loss exceeds the complexity cost.

### Local/server state races

MV3 suspension, offline queue, local drafts, and backend candidate state can diverge.

Mitigation: client-generated IDs, idempotency, explicit state versions, authoritative server state, draft acknowledgment, fixture-based migration and restart E2E.

### Derived projection drift

Current summary/technology/search projection can diverge from `ai_derivations`.

Mitigation: update projection and derivation in one transaction after validated output; periodic invariant check; derived fields rebuildable.

## Future scalability issues

- Raw-event retention deletes can create vacuum/write pressure. Start chunked; partition only after measurements.
- Per-user vector filtering with approximate indexes can reduce recall. Start exact; benchmark filtered recall before HNSW.
- A single job table may contend with OLTP. Separate worker/dequeue table only when queue latency/locks show it.
- Extension API compatibility lags Web Store deployment. Maintain minimum-supported contract window and server kill switch.
- AI model dimension migration is operationally expensive. Fix one embedding model generation for MVP and plan dual-read only when necessary.

## Bottlenecks

1. Product precision, not database throughput: false Recall cards destroy trust.
2. Browser permissions and store review can block distribution.
3. Candidate detection has sparse metadata and no code/terminal context.
4. Users may not write final solutions; no technical system fixes that.
5. Model/provider latency and cost for current-session embeddings can grow if not fingerprinted/rate-limited.

## Security risks

- Sanitizer disagreement between extension/backend.
- Cross-tenant leakage in vector/search queries.
- Sensitive final solutions in logs/support/AI prompts.
- Malicious page titles and extension-message payloads.
- Refresh-token theft from browser profile.
- Incomplete deletion of derived vectors/backups.

Mitigation priorities are encoded in milestone security gates and tests. Broad penetration testing does not replace these architectural controls.

## UX risks created by architecture

- Polling may delay the ghost icon. Accept up to the product latency target; WebSockets are not justified.
- Async enrichment may cause metadata to appear after save. Solution renders first with stable layout.
- Offline/server disagreement may show stale candidate status. Local draft/action remains usable and UI labels sync state.
- Side-panel availability/behavior varies by supported Chrome version and user placement. Pin supported versions and test left/right/narrow layouts.

## AI risks

- Semantic similarity conflates same technology with same problem.
- Generated summary/technology extraction can invent specificity.
- Prompt injection arrives through titles/queries/user text.
- Uncalibrated scores create false confidence.
- Positive dogfood examples hide hard negatives.

Mitigations: hybrid supporting evidence, strict schema/grounding, no tools, deterministic explanations, hard-negative corpus, conservative threshold, one-card suppression.

## Recommended simplifications before implementation

1. Implement lexical/error/technology retrieval before pgvector Recall.
2. Keep content scripts absent.
3. Use one backend Maven module/package tree, not multiple Maven submodules initially.
4. Use database jobs, not a worker service/Redis.
5. Keep technologies/error phrases as bounded arrays and a search projection.
6. Support only email/password authentication in private beta; social login remains deferred.
7. Support current and previous stable Chrome only.
8. Keep one AI provider/model per function.
9. Do not build export, split/merge, session dashboard, or admin UI.
10. Treat the UX Demo Memory as local fixture content, never production AI/data.

## Architecture decision gates

Implementation may begin only after the Ready for Development blockers are resolved and the accepted records in [docs/adr](adr/README.md) are followed. The highest-impact records are:

- [ADR-0001](adr/ADR-0001-java-version.md), [ADR-0002](adr/ADR-0002-build-system.md), and [ADR-0027](adr/ADR-0027-spring-boot-3.md): backend toolchain.
- [ADR-0004](adr/ADR-0004-monorepo-structure.md) and [ADR-0005](adr/ADR-0005-backend-architecture.md): repository and modular-monolith boundaries.
- [ADR-0006](adr/ADR-0006-chrome-manifest-v3.md): extension platform and permissions boundary.
- [ADR-0007](adr/ADR-0007-postgresql-version.md), [ADR-0008](adr/ADR-0008-pgvector-version.md), and [ADR-0012](adr/ADR-0012-vector-storage-strategy.md): database/vector foundation.
- [ADR-0028](adr/ADR-0028-email-password-authentication.md): email/password identity and tokens.
- [ADR-0010](adr/ADR-0010-ai-provider-abstraction.md) and [ADR-0011](adr/ADR-0011-embedding-model-abstraction.md): AI/model boundaries.
- [ADR-0029](adr/ADR-0029-render-deployment.md) and [ADR-0019](adr/ADR-0019-ci-cd-strategy.md): Render deployment and release topology.
- [ADR-0030](adr/ADR-0030-package-namespace.md): `io.github.aayush10016.trail` package namespace; no commercial domain prerequisite.
- [ADR-0031](adr/ADR-0031-observability.md), [ADR-0032](adr/ADR-0032-feature-flags.md), and [ADR-0033](adr/ADR-0033-explainable-ai-provenance.md): production operations and explainability.
