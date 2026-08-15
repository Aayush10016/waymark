# ADR-0016: Testing Framework Stack

- **Status:** Accepted
- **Date:** 2026-07-05
- **Decision owners:** Quality Owner, Module Owners

## Context

Trail spans domain logic, PostgreSQL/pgvector, REST contracts, MV3 lifecycle, React surfaces, security, AI boundaries, and browser E2E.

## Decision

Use JUnit 5 + AssertJ for Java tests, Mockito only at ports, Spring Boot test slices, Testcontainers PostgreSQL/pgvector, and controlled HTTP fakes for email/AI boundaries. Use Vitest + React Testing Library for TypeScript/UI and Playwright with persistent Chromium/unpacked extension for E2E.

## Alternatives considered

- Spock: expressive, adds Groovy/toolchain.
- H2 integration tests: fast, behavior diverges from PostgreSQL/pgvector.
- Jest: mature, less aligned with Vite-native stack.
- Cypress: good web UI, weaker fit for extension persistent-context testing.
- Selenium: broad, heavier and less ergonomic for modern extension tests.

## Why selected

The stack matches production boundaries and provides fast unit feedback plus real database/browser confidence.

## Trade-offs

- Multiple frameworks and slower integration/E2E suites.
- Testcontainers requires Docker.
- Browser E2E can be flaky without strict ownership.

## Risks

- Over-mocking hides integration bugs.
- Coverage targets drive low-value tests.
- CI duration grows.

## One-million-user review

No runtime bottleneck. Team/repository growth requires sharding, fixture ownership, and flake control.

## Future migration path

Partition CI by path/test history, add remote runners, contract test environments, and selective visual/performance suites. Replace a framework only through dual-run equivalence.

## Consequences

H2 is prohibited. Critical privacy/auth/vector behavior requires real PostgreSQL/browser integration tests.
