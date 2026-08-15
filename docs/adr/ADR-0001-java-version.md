# ADR-0001: Java 17 Language and Runtime Baseline

- **Status:** Accepted
- **Date:** 2026-07-05
- **Decision owners:** Technical Lead, Backend Owner

## Context

Trail v1.1 specifies Java 17. The baseline affects language features, libraries, container images, security support, and future JVM upgrades.

## Decision

Compile and run the backend on Java 17. Trail v1.1 pins Eclipse Temurin 17.0.19+10 for development and future CI/container baselines; do not rely on developer-machine defaults. Source and bytecode target remain 17.

## Alternatives considered

- Java 21 LTS: better runtime/language improvements but violates the frozen v1.1 stack.
- Java 25 LTS: newest LTS, higher ecosystem and operational change.
- Java 11: mature but obsolete for a new Spring Boot 3.5 application.

## Why selected

It follows the frozen specification, is supported by Spring Boot 3.5, and has broad tooling/library support.

## Trade-offs

- Forfeit newer language/runtime improvements initially.
- Earlier upgrade pressure than starting on 21/25.
- Lower ecosystem risk and no scope redesign.

## Risks

- Security/support timelines may force an upgrade.
- Engineers may accidentally use newer JDK APIs without release enforcement.

## One-million-user review

Java 17 is not a user-scale bottleneck. Runtime efficiency and support may become operational constraints, but architecture dominates throughput.

## Future migration path

Upgrade one LTS at a time using compiler `release`, CI matrix, dependency compatibility, load tests, and staged container rollout. Keep domain code free from JDK-internal APIs.

## Consequences

ENG-002 pins Eclipse Temurin 17.0.19+10 in repository toolchain metadata. ENG-005 must enforce Java 17 source/bytecode and runtime requirements in Maven, ENG-010 must reproduce the pin in CI, and container issues must use the same distribution until an approved patch update.
