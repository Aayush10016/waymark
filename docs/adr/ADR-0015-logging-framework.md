# ADR-0015: SLF4J with Logback and Structured Content-Free Logs

- **Status:** Accepted
- **Date:** 2026-07-05
- **Decision owners:** Backend Owner, Security Owner

## Context

Trail needs operational diagnostics without leaking URLs, queries, errors, solutions, prompts, embeddings, identity, or tokens.

## Decision

Use SLF4J as the application logging API and Spring Boot-managed Logback runtime. Emit readable text locally and structured JSON in deployed environments using Spring Boot structured logging. Log opaque request/job/user/session identifiers only under the approved classification policy.

## Alternatives considered

- Log4j2: capable async logging, additional configuration/dependency choice.
- Direct vendor telemetry SDK logging: lock-in and accidental content export.
- Java Util Logging: weaker ecosystem/structure.
- No application logs: unacceptable operations/security visibility.

## Why selected

It is Spring Boot’s conventional default, portable across transports, and sufficient when paired with strict content policy/canary tests.

## Trade-offs

- Async/high-volume tuning may be weaker than specialized setups.
- Structured schema requires governance.
- Logs alone do not replace metrics/traces/security events.

## Risks

- Developers interpolate sensitive objects/exceptions.
- MDC/context leakage across threads/jobs.
- Provider/HTTP libraries log bodies by default.

## One-million-user review

Framework is not the bottleneck; ingestion volume/cost/cardinality can be.

## Future migration path

Keep SLF4J API and replace/augment runtime transport asynchronously if profiling proves need. Sample/aggregate, enforce cardinality budgets, and route security audit separately without changing application calls.

## Consequences

HTTP/SQL/provider body logging is disabled. Sensitive-field lint/canary tests block release.
