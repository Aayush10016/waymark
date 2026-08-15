# ADR-0027: Spring Boot 3.5.15

- **Status:** Accepted; supersedes ADR-0003
- **Date:** 2026-07-05
- **Decision owners:** Technical Lead, Backend Owner

## Context

Trail targets Java 17 and prioritizes mature enterprise dependency compatibility over adopting Spring Boot 4.

## Decision

Use Spring Boot 3.5.15 and remain on the supported 3.5.x line, applying reviewed patch upgrades. Do not use Spring Boot 4.x in Trail v1.1.

## Alternatives considered

- Spring Boot 4.1.x: current major and longer forward runway, but newer Spring Framework/Security ecosystem.
- Spring Boot 3.4.x: mature but older feature line.
- Other Java frameworks: violate the frozen stack.

## Why selected

3.5.15 is the latest stable 3.x line, supports Java 17, and provides mature Spring Security, Actuator, Micrometer, JPA, validation, and testing integration with broad enterprise familiarity.

## Trade-offs

- A future Boot 4 migration is inevitable.
- Shorter support runway than starting on 4.x.
- Lower third-party compatibility and hiring/onboarding risk now.

## Risks

- Staying on 3.x after support ends.
- Libraries independently moving to Spring Framework 7/Boot 4.

## One-million-user review

Not a throughput bottleneck. Support/security lifecycle is the relevant risk.

## Future migration path

Keep deprecation warnings at zero, framework code in adapters, and managed dependencies unoverridden. Upgrade to Boot 4 through official migration guides, compatibility CI, integration/E2E tests, and staged containers when support or required capabilities justify it.

## Consequences

ENG-002 pins 3.5.15 as the only supported baseline. ENG-005 must encode that version in the backend POM. Dependency automation may propose reviewed 3.5.x patches but cannot cross to 4.x without a superseding ADR.
