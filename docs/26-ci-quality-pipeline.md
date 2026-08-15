# CI Quality Pipeline

Status: ENG-010 baseline.

## Purpose

The initial GitHub Actions quality pipeline protects `main` with format, lint, unit, contract, build, secret, and dependency checks.

## Required GitHub check

Configure branch protection for `main` to require:

```text
quality / format-lint-test-contract-build-security
```

This repository cannot enforce branch protection from source files alone; the workflow defines the required check and repository settings must require it.

## Trigger policy

The quality workflow runs on:

- pull requests targeting `main`;
- pushes to `main`.

## Checks

| Gate | Command or action |
|---|---|
| Format | `pnpm ci:format` |
| Secret scan | `pnpm ci:secrets` |
| Dependency review | `actions/dependency-review-action` on pull requests |
| Dependency health | `pnpm ci:dependencies` |
| TypeScript lint/type/test/build | `pnpm verify` |
| OpenAPI lint/drift | `pnpm verify` through `pnpm openapi:check` |
| Java unit/integration/build | `./mvnw verify` |

## Toolchains

- Java: Eclipse Temurin `17.0.19+10`.
- Maven: repository Maven Wrapper, Maven `3.9.16`.
- Node.js: `24.18.0`.
- pnpm: `11.10.0`.
- Spring Boot: `3.5.15`.

## Action pinning

Actions are pinned to immutable commit SHAs. The workflow includes comments mapping each SHA to the reviewed upstream tag.

Current pins:

| Action | Reviewed tag | SHA |
|---|---|---|
| `actions/checkout` | `v7.0.0` | `9c091bb21b7c1c1d1991bb908d89e4e9dddfe3e0` |
| `actions/setup-java` | `v5.5.0` | `0f481fcb613427c0f801b606911222b5b6f3083a` |
| `actions/setup-node` | `v6.4.0` | `48b55a011bda9f5d6aeb4c2d9c7362e8dae4041e` |
| `actions/dependency-review-action` | `v5.0.0` | `a1d282b36b6f3519aa1f3fc636f609c47dddb294` |

Changing any pin requires review of upstream release notes and a normal pull request.

