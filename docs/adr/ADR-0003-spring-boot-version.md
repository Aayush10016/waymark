# ADR-0003: Spring Boot 4.1.x

- **Status:** Superseded by [ADR-0027](ADR-0027-spring-boot-3.md)
- **Date:** 2026-07-05
- **Decision owners:** Technical Lead, Backend Owner

## Context

Trail requires Spring Security, REST, validation, PostgreSQL, observability, configuration, and container packaging on Java 17.

## Decision

Start on Spring Boot 4.1.x, pinning the current patch at implementation and updating patches through reviewed dependency PRs. Use the managed Spring dependency platform; avoid overriding managed versions without an ADR or documented compatibility need.

## Alternatives considered

- Spring Boot 3.5.x: mature and supported, but creates an earlier major migration.
- Spring Boot 4.0.x: supported but superseded by 4.1 feature line.
- Quarkus/Micronaut: different ecosystem and violates the specified stack.

## Why selected

4.1 is the current stable line and supports Java 17. Starting current avoids an immediate Spring Framework/Security major migration.

## Trade-offs

- Newer major ecosystem means less accumulated production history.
- Some third-party libraries may lag Jakarta/Spring 7 compatibility.
- Better support horizon and current security baseline.

## Risks

- Extension libraries such as OpenAPI/security/test integrations may have compatibility gaps.
- Pinning a feature line without patch discipline creates vulnerabilities.

## One-million-user review

Framework version is not a direct bottleneck. Threading, database, and deployment configuration matter more.

## Future migration path

Apply patch releases continuously; feature/major upgrades use Spring migration guides, deprecation-free builds, integration/E2E suites, and staged deployment. Keep domain code framework-free.

## Consequences

ENG-001 must run a compatibility spike for required starters/libraries before accepting the final patch pin.
