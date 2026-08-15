# ADR-0031: Lightweight Spring Observability

- **Status:** Accepted
- **Date:** 2026-07-05
- **Decision owners:** Platform Owner, Security Owner

## Context

MVP needs production health, metrics, and diagnostics without deploying an observability platform.

## Decision

Use Spring Boot Actuator, Micrometer, and SLF4J/Logback structured JSON logging. Expose `/actuator/health`, `/actuator/health/liveness`, and `/actuator/health/readiness`. Expose `/actuator/metrics` only through an authenticated/private management boundary. Render checks readiness. Do not add Prometheus, Grafana, OpenTelemetry, or distributed tracing in v1.1.

## Alternatives considered

- Logs only: insufficient health/metrics.
- Full Prometheus/Grafana/OTel stack: operational overkill.
- Vendor agent/APM: fast insight but lock-in and data-leak risk.

## Why selected

Spring-native observability provides portable signals with minimal infrastructure and supports Render health checks.

## Trade-offs

- Limited cross-request/job tracing.
- Platform metrics/log UI is the initial operational surface.
- Management endpoint security requires care.

## Risks

- Sensitive/high-cardinality tags.
- Public Actuator exposure.
- Readiness checking dependencies too aggressively and causing outages.

## One-million-user review

Metrics volume and incident diagnosis will outgrow this setup.

## Future migration path

Add Prometheus-compatible scraping/managed metrics first, then dashboards/alerts. Add OpenTelemetry only when multiple deployables or tracing questions justify it. Preserve Micrometer/SLF4J instrumentation.

## Consequences

Health payloads reveal no internals. Metrics names/tags are reviewed for cardinality and content safety.
