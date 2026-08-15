# Trail v1.1 Engineering Milestones and Issue Backlog

Status: frozen implementation backlog
Planning unit: focused engineering hours including tests, documentation, and review fixes
Issue size rule: 4–8 hours; anything larger must be split before assignment

## Labels

- Areas: `area:repo`, `area:backend`, `area:extension`, `area:frontend`, `area:database`, `area:api`, `area:ai`, `area:security`, `area:privacy`, `area:infra`, `area:quality`.
- Types: `type:spike`, `type:feature`, `type:test`, `type:docs`, `type:security`, `type:release`.
- Risks: `risk:privacy`, `risk:security`, `risk:ai`, `risk:ux`, `risk:migration`.
- Priorities: `priority:p0`, `priority:p1`.

Each table row is one GitHub issue and contains the required description, acceptance criteria, dependencies, labels, and effort.

## M0 — Repository and architecture foundation

| ID | Description | Acceptance criteria | Dependencies | Labels | Hours |
|---|---|---|---|---|---:|
| ENG-001 | Freeze `io.github.aayush10016.trail` as the Java package namespace. | ADR-0030 is accepted without a domain-control condition; all current architecture documents agree on the namespace; no Java source is created as part of this issue. | None | `area:repo type:docs priority:p0` | 4 |
| ENG-002 | Pin JDK 17 distribution/patch, Spring Boot 3.5.15, and Maven 3.9.16. | Version files/ADR references agree; clean-machine resolution documented; Boot 4 blocked. | ENG-001 | `area:repo area:backend type:feature priority:p0` | 4 |
| ENG-003 | Pin Node, pnpm, TypeScript, React, and extension build-tool versions. | Exact versions documented; lock strategy approved; supported Chrome versions recorded. | ENG-001 | `area:repo area:extension type:feature priority:p0` | 4 |
| ENG-004 | Establish root monorepo boundaries and ownership metadata. | Target directories/READMEs/CODEOWNERS boundaries exist; no generic shared directory. | ENG-001–003 | `area:repo type:feature priority:p0` | 6 |
| ENG-005 | Establish backend Maven/Spring Boot skeleton and architecture checks. | One module starts/tests; `io.github.aayush10016.trail` capability packages compile; forbidden dependency tests exist. | ENG-002, ENG-004 | `area:backend type:feature priority:p0` | 8 |
| ENG-006 | Establish pnpm workspace and shared TypeScript configurations. | Extension/packages resolve; strict configs/lint commands run; generated paths are isolated. | ENG-003, ENG-004 | `area:repo area:frontend type:feature priority:p0` | 6 |
| ENG-007 | Create OpenAPI v1 skeleton and generated-client pipeline. | Auth/capture/problem/search/privacy resource shells lint; generated client compiles; drift fails. | ENG-004–006 | `area:api type:feature priority:p0` | 8 |
| ENG-008 | Validate Chrome MV3 permissions, side panel, and worker lifecycle. | Exact warnings/events/gestures/restart behavior documented; no identity/content-script permission. | ENG-003, ENG-006 | `area:extension type:spike risk:privacy priority:p0` | 8 |
| ENG-009 | Establish PostgreSQL 17/pgvector 0.8.2 local and Testcontainers baseline. | Versions match ADRs; extension enabled; connectivity/health test passes. | ENG-002, ENG-004 | `area:database area:infra type:feature priority:p0` | 6 |
| ENG-010 | Establish initial GitHub Actions quality pipeline. | Format/lint/unit/contract/build/secret/dependency checks protect main; actions pinned. | ENG-004–009 | `area:quality area:repo type:feature risk:security priority:p0` | 8 |

## M1 — Identity, platform, observability, and flags

| ID | Description | Acceptance criteria | Dependencies | Labels | Hours |
|---|---|---|---|---|---:|
| ENG-011 | Add users, auth sessions, and email-action-token Flyway migrations. | Constraints/indexes/hash-only token storage verified in PostgreSQL integration tests. | ENG-009 | `area:database area:security type:feature priority:p0` | 8 |
| ENG-012 | Define Argon2id password policy and rehash strategy. | Benchmarked parameters, maximum input size, version metadata, and tests documented. | ENG-005 | `area:security type:security priority:p0` | 4 |
| ENG-013 | Implement registration application/domain flow. | Normalized unique email; Argon2id hash; generic conflict behavior; unit tests pass. | ENG-011, ENG-012 | `area:backend area:security type:feature priority:p0` | 8 |
| ENG-014 | Implement email verification token lifecycle. | Random single-use expiring hashed token; consume/resend/generic response tests pass. | ENG-011, ENG-013 | `area:backend area:security type:feature priority:p0` | 8 |
| ENG-015 | Add email delivery port and local/mock adapter. | Verification/reset templates contain no secrets in logs; delivery failure is typed/retryable. | ENG-014 | `area:backend type:feature risk:security priority:p0` | 6 |
| ENG-016 | Implement login and asymmetric JWT issuance. | Verified account required; generic errors; claims/algorithm/audience/expiry tests pass. | ENG-011–014 | `area:backend area:security type:feature priority:p0` | 8 |
| ENG-017 | Implement refresh rotation and family reuse detection. | Raw token never stored; rotation/replay/concurrency/revocation tests pass. | ENG-016 | `area:backend area:security type:feature priority:p0` | 8 |
| ENG-018 | Implement logout/current-session revocation. | Current family revoked idempotently; expired/revoked behavior contract-tested. | ENG-017 | `area:backend area:api type:feature priority:p0` | 4 |
| ENG-019 | Implement password-reset request lifecycle. | Generic response; rate-limit hook; hashed single-use token and expiry tests pass. | ENG-011, ENG-015 | `area:backend area:security type:feature priority:p0` | 6 |
| ENG-020 | Implement password reset and session revocation. | Password policy enforced; token consumed once; all refresh families revoked; tests pass. | ENG-017, ENG-019 | `area:backend area:security type:feature priority:p0` | 8 |
| ENG-021 | Add extension auth storage and token-refresh service. | Access token memory-only; refresh metadata minimized; retries preserve drafts; message schemas tested. | ENG-006, ENG-016–018 | `area:extension area:security type:feature priority:p0` | 8 |
| ENG-022 | Build registration/sign-in/verification/reset onboarding UI. | No social buttons; generic errors; keyboard/focus/offline/draft behavior matches UX. | ENG-007, ENG-013–021 | `area:frontend area:extension type:feature risk:ux priority:p0` | 8 |
| ENG-023 | Add authentication abuse and enumeration test matrix. | Registration/login/reset/verification/IP-account throttle cases and log canaries pass. | ENG-013–022 | `area:security area:quality type:test priority:p0` | 8 |
| ENG-024 | Add Actuator, Micrometer, health groups, and structured JSON logging. | Minimal health/liveness/readiness; private metrics/details; content-safe log canaries pass. | ENG-005, ENG-010 | `area:infra area:quality type:feature risk:privacy priority:p0` | 8 |
| ENG-025 | Add typed configuration-backed feature flags. | AI Suggestions, Ghost Replay, Semantic Search, Experimental flags validated with owner/default/removal; security controls unflaggable. | ENG-005 | `area:backend type:feature priority:p0` | 6 |

## M2 — Trusted capture pipeline

| ID | Description | Acceptance criteria | Dependencies | Labels | Hours |
|---|---|---|---|---|---:|
| ENG-026 | Create language-neutral URL/query sanitation fixture corpus. | Schemes, credentials, private hosts, IDN/IP, trackers, secrets, search allowlist covered. | ENG-008 | `area:security type:test risk:privacy priority:p0` | 6 |
| ENG-027 | Implement extension sanitation and local policy adapter. | Fixture corpus passes; unsafe events never queue; policy version recorded. | ENG-026 | `area:extension area:security type:feature priority:p0` | 8 |
| ENG-028 | Implement backend sanitation and ingestion validation. | Same corpus passes; backend never more permissive; unknown/oversize fields rejected. | ENG-026, ENG-005 | `area:backend area:security type:feature priority:p0` | 8 |
| ENG-029 | Build permission explanation and consent persistence UI. | Permission requested after user gesture; consent off by default; revoked/denied states pass. | ENG-008, ENG-022 | `area:extension area:frontend type:feature risk:privacy priority:p0` | 8 |
| ENG-030 | Implement top-level navigation listener and resource mapping. | Top-frame only; redirects/history-state events deduplicated; worker suspension tests pass. | ENG-008, ENG-027 | `area:extension type:feature priority:p0` | 8 |
| ENG-031 | Define IndexedDB capture queue schema and migrations. | Transactional queue, size/count metadata, upgrade/recovery fixtures pass. | ENG-006, ENG-027 | `area:extension type:feature risk:migration priority:p0` | 6 |
| ENG-032 | Implement queue write, batch selection, and capacity policy. | Write-before-network; sequence/order/caps; final-solution drafts never evicted. | ENG-031 | `area:extension type:feature priority:p0` | 8 |
| ENG-033 | Add capture-event and consent database migrations. | Unique idempotency, ownership, retention, and critical indexes verified. | ENG-009, ENG-028 | `area:database type:feature priority:p0` | 6 |
| ENG-034 | Implement capture-batch endpoint and per-event results. | Auth/consent/rate/idempotency/partial validation tests pass; no content logs. | ENG-007, ENG-028, ENG-033 | `area:backend area:api type:feature priority:p0` | 8 |
| ENG-035 | Implement extension batch sync and acknowledgment reconciliation. | Accepted/duplicate/permanent/retryable paths; 401/413/429/offline behavior tested. | ENG-021, ENG-032, ENG-034 | `area:extension type:feature priority:p0` | 8 |
| ENG-036 | Implement local pause/resume and domain exclusions. | Applies before persistence; survives restart; current-domain action and undo pass. | ENG-027, ENG-029 | `area:extension area:privacy type:feature priority:p0` | 6 |
| ENG-037 | Implement capture-state API and popup priority model. | Recording/active/offline/auth/permission states expose no raw activity and match UX priority. | ENG-034–036 | `area:api area:extension area:frontend type:feature priority:p0` | 8 |
| ENG-038 | Implement raw capture retention job. | Chunked/idempotent expiry; solved artifacts survive; lock/performance test passes. | ENG-033, ENG-034 | `area:database area:privacy type:feature priority:p0` | 8 |
| ENG-039 | Add capture privacy/restart E2E suite. | Packet, restart, duplicate, exclusion, consent, offline, and auth-expiry scenarios pass. | ENG-029–038 | `area:quality area:extension type:test risk:privacy priority:p0` | 8 |

## M3 — Verified Problem Session loop

| ID | Description | Acceptance criteria | Dependencies | Labels | Hours |
|---|---|---|---|---|---:|
| ENG-040 | Implement Problem Session domain state machine. | Illegal transitions fail; solved requires exact user-authored solution; unit tests complete. | ENG-005 | `area:backend type:feature priority:p0` | 6 |
| ENG-041 | Add Problem Session and artifact migrations/repositories. | Composite ownership, active positions, status/search indexes, concurrency tests pass. | ENG-009, ENG-040 | `area:database area:backend type:feature priority:p0` | 8 |
| ENG-042 | Implement deterministic detection feature extraction. | Bounded time/query/domain/error features and reason codes pass golden fixtures. | ENG-034, ENG-041 | `area:backend area:ai type:feature risk:ai priority:p0` | 8 |
| ENG-043 | Implement candidate scoring, boundaries, and suppression. | No model per event; manual precedence; inactivity/topic shift/dismiss constraints tested. | ENG-042 | `area:backend area:ai type:feature risk:ux priority:p0` | 8 |
| ENG-044 | Implement manual start and finish API commands. | Ownership/idempotency/version/state responses contract-tested. | ENG-040, ENG-041 | `area:backend area:api type:feature priority:p0` | 6 |
| ENG-045 | Build active-session popup and detection evidence. | No auto-open/focus theft; Start/Finish/Not problem/Why detected states pass. | ENG-037, ENG-043, ENG-044 | `area:frontend area:extension type:feature risk:ux priority:p0` | 8 |
| ENG-046 | Implement candidate detail and ordered artifact API. | User-scoped stable order; bounded response; ETag and missing states tested. | ENG-041, ENG-043 | `area:backend area:api type:feature priority:p0` | 8 |
| ENG-047 | Implement local final-solution draft repository. | Autosave, migration, restart, offline, acknowledged deletion, capacity tests pass. | ENG-031 | `area:extension type:feature priority:p0` | 6 |
| ENG-048 | Build End Session outcome step. | Solved/Not yet/Not problem/Remind transitions, focus, reduced motion pass. | ENG-045–047 | `area:frontend area:extension type:feature risk:ux priority:p0` | 8 |
| ENG-049 | Build final-solution entry and context review step. | No AI suggestion; draft persistence; remove artifact; under-30-second usability target tested. | ENG-047, ENG-048 | `area:frontend area:extension type:feature priority:p0` | 8 |
| ENG-050 | Implement idempotent solution endpoint and job creation. | Exact text/authorship/version/conflict/retry/cross-user tests pass; commit precedes AI. | ENG-040, ENG-041, ENG-049 | `area:backend area:api area:security type:feature priority:p0` | 8 |
| ENG-051 | Build solution-first Problem Summary. | Exact solution before generated fields; loading/offline/error/edit states pass. | ENG-046, ENG-050 | `area:frontend type:feature priority:p0` | 8 |
| ENG-052 | Implement safe resource replay command. | HTTP(S)/private/count validation; confirmation/hard cap; order unchanged. | ENG-046, ENG-051 | `area:extension area:security type:feature priority:p0` | 6 |
| ENG-053 | Add complete Problem Session loop E2E. | Capture → candidate → finish → draft → solution → summary → replay passes online/offline/restart. | ENG-045–052 | `area:quality type:test priority:p0` | 8 |

## M4 — Search, AI, embeddings, and explainability

| ID | Description | Acceptance criteria | Dependencies | Labels | Hours |
|---|---|---|---|---|---:|
| ENG-054 | Add background-job migration and leasing repository. | Ready/lease/expiry/dedup indexes and concurrent claim tests pass. | ENG-009 | `area:database area:backend type:feature priority:p0` | 8 |
| ENG-055 | Implement scheduler, retry, and terminal job semantics. | Network calls outside transaction; capped backoff/idempotency/metrics tests pass. | ENG-054, ENG-024 | `area:backend type:feature priority:p0` | 8 |
| ENG-056 | Define `AIProvider` port and mock adapter. | Provider-neutral structured contract; mock supports success/invalid/timeout; no SDK leakage. | ENG-005, ENG-025 | `area:ai type:feature priority:p0` | 4 |
| ENG-057 | Implement selected production `AIProvider` adapter. | Config selection, timeout, schema mapping, safe errors, cost metadata tests pass. | ENG-056 | `area:ai area:backend type:feature risk:ai priority:p0` | 8 |
| ENG-058 | Implement prompt registry and immutable version identity. | Key/version/hash/schema lookup; duplicate/change/golden tests pass. | ENG-056 | `area:ai type:feature priority:p0` | 6 |
| ENG-059 | Implement AI redaction and refusal policy. | Secret/PII/private-path/adversarial fixtures pass; insufficient safe input refuses call. | ENG-026, ENG-056 | `area:ai area:security type:feature risk:privacy priority:p0` | 8 |
| ENG-060 | Add AI derivation and provenance migrations. | Provider/model/prompt/schema/policy/fingerprint/status/cost constraints/indexes pass. | ENG-041, ENG-058 | `area:database area:ai type:feature priority:p0` | 8 |
| ENG-061 | Implement structured Problem Session enrichment job. | Grounded output/projected fields atomic; invalid/insufficient/outage behavior tested. | ENG-050, ENG-055–060 | `area:ai area:backend type:feature risk:ai priority:p0` | 8 |
| ENG-062 | Define `EmbeddingProvider` port and mock adapter. | Provider-neutral vector metadata; dimension validation; no `AIProvider` coupling. | ENG-005, ENG-025 | `area:ai type:feature priority:p0` | 4 |
| ENG-063 | Implement selected production `EmbeddingProvider` adapter. | Config selection, timeout, dimension/model metadata, cost and error tests pass. | ENG-062 | `area:ai type:feature risk:ai priority:p0` | 8 |
| ENG-064 | Add problem-embedding migration and repository. | Fixed dimension/generation/active uniqueness/composite ownership/deletion tests pass. | ENG-009, ENG-062 | `area:database area:ai type:feature priority:p0` | 6 |
| ENG-065 | Implement canonical embedding document and solved-session job. | Fingerprint/version/bounds/cache/failure tests pass; no per-page calls. | ENG-050, ENG-059, ENG-063, ENG-064 | `area:ai area:backend type:feature priority:p0` | 8 |
| ENG-066 | Implement weighted lexical/error/technology search query. | User scope, weights, deterministic evidence, query plans and hard negatives pass. | ENG-041, ENG-050 | `area:database area:backend type:feature priority:p0` | 8 |
| ENG-067 | Implement sensitive search API, filters, and cursor. | POST body, page cap, filter fingerprint, lexical degradation, contract tests pass. | ENG-007, ENG-066 | `area:api area:backend type:feature priority:p0` | 8 |
| ENG-068 | Implement exact user-scoped vector candidate query. | Active generation/user scope/query timeout/distance tests and plans pass. | ENG-064, ENG-065 | `area:database area:ai type:feature priority:p0` | 6 |
| ENG-069 | Implement hybrid similarity ranker and threshold policy. | Requires supporting evidence; same-tech hard negatives fail; algorithm version recorded. | ENG-066, ENG-068 | `area:ai area:backend type:feature risk:ai priority:p0` | 8 |
| ENG-070 | Add recommendation provenance and event migrations. | Retrieved IDs/ranks/evidence/provider/model/embedding/algorithm/outcome constraints pass. | ENG-060, ENG-069 | `area:database area:ai type:feature priority:p0` | 8 |
| ENG-071 | Implement deterministic `Why this?` explanation builder. | Every message maps to evidence codes/IDs; no generated rationale/raw score; tests pass. | ENG-069, ENG-070 | `area:ai area:backend type:feature risk:ai priority:p0` | 6 |
| ENG-072 | Create similarity golden corpus and evaluation report. | Positive/same-tech/same-error-different-cause/unrelated cohorts; precision gate documented. | ENG-061, ENG-069 | `area:ai area:quality type:test priority:p0` | 8 |
| ENG-073 | Add AI outage, cost-budget, injection, and provenance tests. | Capture/solution/exact search survive; budget/refusal/retry/log canaries/provenance pass. | ENG-057–072 | `area:ai area:security area:quality type:test priority:p0` | 8 |

## M5 — Recall and Ghost Replay

| ID | Description | Acceptance criteria | Dependencies | Labels | Hours |
|---|---|---|---|---|---:|
| ENG-074 | Implement top recommendation lifecycle/query. | One card/current candidate; strong/possible/no-match rules; ownership and suppression pass. | ENG-069–071 | `area:backend area:ai type:feature priority:p0` | 8 |
| ENG-075 | Implement recommendation API and capture-state summary. | 204 no match; bounded exact solution/evidence; ETag/contract/security tests pass. | ENG-074 | `area:api area:backend type:feature priority:p0` | 6 |
| ENG-076 | Wire AI Suggestions, Ghost Replay, and Semantic Search flags. | Server-side global gates; safe defaults/fallbacks; no persisted-state corruption. | ENG-025, ENG-067, ENG-075 | `area:backend type:feature priority:p0` | 4 |
| ENG-077 | Implement Recall invocation and no-match routing. | Toolbar/shortcut/user gesture; side panel opens; no match routes to search; focus restored. | ENG-008, ENG-075 | `area:extension area:frontend type:feature risk:ux priority:p0` | 8 |
| ENG-078 | Build Similar Problem Found card. | Exact solution, age, duration, evidence, Why this, no score/time-saved; all states pass. | ENG-071, ENG-075, ENG-077 | `area:frontend type:feature priority:p0` | 8 |
| ENG-079 | Build Ghost Replay panel transformation and motion. | Choreography/reduced-motion/left-right/narrow panel/focus states match UX. | ENG-078 | `area:frontend type:feature risk:ux priority:p0` | 8 |
| ENG-080 | Implement Ghost Replay resource progression. | Zero auto-tabs; decisive/next/opened-now/already-visited/return behavior passes. | ENG-052, ENG-079 | `area:extension area:frontend type:feature priority:p0` | 8 |
| ENG-081 | Implement recommendation event API. | Server-derived shown; allowlisted outcomes; idempotency/order/ownership tests pass. | ENG-070, ENG-075 | `area:api area:backend type:feature priority:p0` | 6 |
| ENG-082 | Implement not-related/dismiss suppression. | Same pair cannot repeat in candidate; helpful/opened states persist without online training. | ENG-074, ENG-081 | `area:backend area:ai type:feature priority:p0` | 6 |
| ENG-083 | Build Recall search side-panel/full-page UI. | Query/current chip/results/evidence/recent/degraded/loading/error/keyboard states pass. | ENG-067, ENG-077 | `area:frontend type:feature priority:p0` | 8 |
| ENG-084 | Build full Timeline modes. | Historical immutable/candidate remove/Ghost opened-now/group repeats/keyboard states pass. | ENG-046, ENG-051, ENG-080 | `area:frontend type:feature priority:p0` | 8 |
| ENG-085 | Add Recall/Ghost Replay E2E suite. | Strong/no-match/not-related/helpful/resource/focus/offline/AI-failure flows pass. | ENG-076–084 | `area:quality type:test risk:ai risk:ux priority:p0` | 8 |

## M6 — Privacy, security, resilience, and performance

| ID | Description | Acceptance criteria | Dependencies | Labels | Hours |
|---|---|---|---|---|---:|
| ENG-086 | Implement Problem Session deletion transaction/job. | Immediate read exclusion; artifacts/derivations/vectors/events/jobs/provenance removed; retry tests pass. | ENG-060, ENG-064, ENG-070 | `area:privacy area:backend type:feature priority:p0` | 8 |
| ENG-087 | Implement account deletion and local cleanup. | Reauth; sessions revoked first; all user data purged; backup disclosure/local drafts handled. | ENG-020, ENG-086 | `area:privacy area:security type:feature priority:p0` | 8 |
| ENG-088 | Harden offline queue/draft reconciliation. | Stale state, restart, reconnect ordering, pressure, sign-out warning E2E pass. | ENG-039, ENG-053 | `area:extension area:quality type:feature priority:p0` | 8 |
| ENG-089 | Complete endpoint IDOR and mass-assignment matrix. | Every user resource/search/vector/recommendation has negative tests; unknown fields rejected. | ENG-067, ENG-075, ENG-081, ENG-087 | `area:security area:quality type:test priority:p0` | 8 |
| ENG-090 | Complete JWT/refresh/password security matrix. | Algorithm/audience/expiry/skew/replay/reuse/reset/enumeration/throttle cases pass. | ENG-023, ENG-087 | `area:security type:test priority:p0` | 8 |
| ENG-091 | Complete XSS/CSP/message/URL security suite. | Malicious titles/solutions/messages/URLs render inert; privileged sender/schema checks pass. | ENG-027, ENG-052, ENG-084 | `area:security area:extension type:test priority:p0` | 8 |
| ENG-092 | Complete SQL/prompt-injection and log-canary suite. | Dynamic filters parameterized; adversarial model data inert; protected strings absent from logs/metrics. | ENG-059, ENG-067, ENG-073 | `area:security area:quality type:test priority:p0` | 8 |
| ENG-093 | Add database growth and retention performance fixture. | Capture growth/cleanup/vacuum/index/job-table behavior measured without long locks. | ENG-038, ENG-055, ENG-086 | `area:database area:quality type:test priority:p0` | 8 |
| ENG-094 | Benchmark lexical/vector/search latency and query plans. | p95 targets/plans/skewed users documented; no HNSW without failed threshold. | ENG-066–069 | `area:database area:quality type:test priority:p0` | 8 |
| ENG-095 | Benchmark startup, heap, and AI job concurrency on Render-equivalent limits. | Readiness < target or remediation; heap/concurrency limits documented; no premature tuning. | ENG-024, ENG-055, ENG-073 | `area:infra area:quality type:test priority:p0` | 8 |
| ENG-096 | Complete accessibility and visual-state audit. | 18 screens, keyboard, focus, 200% zoom, reduced motion, dark/light, left/right panel pass. | ENG-022, ENG-053, ENG-085 | `area:frontend area:quality type:test priority:p0` | 8 |
| ENG-097 | Write and exercise incident/deletion/provider/auth runbooks. | Owners, detection, mitigation, recovery, verification; tabletop findings resolved. | ENG-024, ENG-073, ENG-087, ENG-090 | `area:infra area:security type:docs priority:p0` | 8 |

## M7 — Render deployment and private-beta release

| ID | Description | Acceptance criteria | Dependencies | Labels | Hours |
|---|---|---|---|---|---:|
| ENG-098 | Validate Render PostgreSQL 17/pgvector compatibility and access controls. | Version/extension/internal URL/pooling/backups/external restriction documented and tested. | ENG-009, ADR-0029 | `area:infra area:database type:spike priority:p0` | 6 |
| ENG-099 | Build production backend Docker image. | Non-root/minimal/pinned/scanned/no secrets; health/readiness and graceful shutdown pass. | ENG-005, ENG-024 | `area:infra type:feature priority:p0` | 8 |
| ENG-100 | Define Render environment and secret configuration. | Web service/database/health/env/secret/region contract documented; no Kubernetes/provider lock in app. | ENG-098, ENG-099 | `area:infra type:feature risk:security priority:p0` | 6 |
| ENG-101 | Deploy staging and verify Flyway/health/metrics privacy. | Migration/readiness/liveness/private metrics/internal DB/log redaction pass. | ENG-024, ENG-098–100 | `area:infra area:quality type:release priority:p0` | 8 |
| ENG-102 | Exercise Render backup/restore and rollback. | RPO/RTO measured; restore integrity, image rollback, migration compatibility documented. | ENG-101 | `area:infra area:database type:test priority:p0` | 8 |
| ENG-103 | Prepare Chrome Web Store package and disclosure. | Reproducible artifact; permissions/network match; no source maps/secrets/remote code; rollout/rollback approved. | ENG-085, ENG-091, ENG-096 | `area:extension area:security type:release priority:p0` | 8 |
| ENG-104 | Freeze critical release regression suite. | Clean/upgraded profiles, API contract, migration, auth, solve, Recall, deletion tests gate release. | ENG-085, ENG-087–103 | `area:quality type:test type:release priority:p0` | 8 |
| ENG-105 | Configure private-beta cohort and support controls. | Consent/revoke, content-hidden support, opt-in evaluation, security contact, owners active. | ENG-097, ENG-101, ENG-103 | `area:privacy area:quality type:release priority:p0` | 6 |
| ENG-106 | Execute v1.1 release readiness review. | No P0 blocker; security/performance/UX/restore/disclosure gates signed; 72-hour monitoring plan active. | ENG-102–105 | `area:quality area:infra type:release priority:p0` | 6 |

## Capacity summary

| Milestone | Issues | Estimated hours |
|---|---:|---:|
| M0 | 10 | 62 |
| M1 | 15 | 106 |
| M2 | 14 | 104 |
| M3 | 14 | 104 |
| M4 | 20 | 144 |
| M5 | 12 | 86 |
| M6 | 12 | 96 |
| M7 | 9 | 64 |
| **Total** | **106** | **766** |

The increased count reflects real decomposition, not added product scope. Five engineers can parallelize after dependencies clear. A single engineer can execute every issue sequentially, but 766 focused hours plus coordination/rework exceeds four calendar months.

## Deferred backlog

- Social/OIDC login.
- Candidate split/merge.
- AI conversation import, IDE/terminal context, cross-browser support.
- User-targeted/percentage feature flags and flag administration UI.
- Grafana, Prometheus, OpenTelemetry, distributed tracing.
- Export, team sharing, billing, admin UI.
- HNSW, Redis, worker extraction, partitioning, Kubernetes, multi-region, dedicated search/vector service without measured trigger.

Deferred work must not enter an active milestone while solution completion or Recall precision misses its gate.
