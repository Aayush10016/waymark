# Critical Release Regression Suite v1

ENG-104 freezes the minimum release-blocking regression gate for Trail v1.1.

## Required gates

| Gate | Required command or evidence | Coverage |
|---|---|---|
| Clean profile | `.\mvnw.cmd verify` and `corepack pnpm verify` from a clean checkout | Full backend and extension baseline. |
| Upgraded profile | CI uses Java 17 and Node 24.18.0 with pinned Maven and pnpm | Toolchain drift detection. |
| API contract | `corepack pnpm openapi:check` | OpenAPI and generated TypeScript client stay current. |
| Migration | PostgreSQL 17/pgvector Testcontainers migration tests | Forward-only Flyway schema and pgvector compatibility. |
| Auth | Identity/JWT/refresh/password/account-deletion suites | Authentication, authorization, and deletion safety. |
| Solve | Problem Session lifecycle, final solution, draft, and summary tests | Core engineering-memory save path. |
| Recall | Search, recommendation, Similar Problem Found, and Ghost Replay tests | Core engineering-memory retrieval path. |
| Deletion | Problem Session deletion, account deletion, retention, and local cleanup tests | Privacy and user-owned data removal. |
| Release package | Extension manifest/Web Store validation and backend Docker image smoke test | Release artifact safety. |

## Release blocking rule

Any failed gate blocks release unless the release owner records a scoped risk acceptance. Security, privacy, authentication, deletion, migration, and package-integrity failures cannot be waived for private beta.

## Deferred

ENG-104 freezes the regression suite and release gate. It does not configure private-beta cohorts, support contacts, 72-hour monitoring, or final release sign-off.
