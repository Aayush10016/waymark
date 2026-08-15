# Render runtime readiness benchmark v1

## Scope

ENG-095 measures Trail backend startup, heap visibility, and AI/background-job concurrency posture against the v1.1 Render deployment assumptions. This is a readiness fixture, not production tuning.

## Frozen targets

| Area | Target | Source |
|---|---:|---|
| Backend cold startup/readiness | Under 30 seconds | `docs/15-scalability.md` |
| Background job processing | Bounded batch, no unbounded worker fan-out | ADR-0023 |
| AI provider calls | Asynchronous; never block final-solution save | `docs/12-ai-pipeline.md` |
| Heap/container memory | Measured before increasing instance size | `docs/15-scalability.md` |

## ENG-095 result

The automated runtime fixture starts the Spring Boot backend with the existing configuration and asserts readiness within the 30 second target. It also records JVM heap visibility through `Runtime.maxMemory()` so constrained runtime behavior can be compared when executed under a Render-equivalent container memory limit.

The background-job runtime remains intentionally conservative:

- one in-process scheduler;
- one scheduler cycle claims a bounded batch per registered job type;
- default batch size is 10;
- provider work is invoked through existing `AIProvider` and `EmbeddingProvider` abstractions;
- no live provider API or production credential is required by the benchmark.

## Remediation policy

If readiness exceeds 30 seconds on the selected Render plan, remediate in this order:

1. inspect startup logs and Spring bean initialization;
2. verify no provider or database network call is on the critical startup path;
3. reduce eager work or disable nonessential startup checks;
4. cap background-job processing further if API readiness is affected;
5. only then consider a larger instance size.

Do not add Redis, a separate worker service, or a larger instance without a measured miss.

## Deferred

ENG-095 does not define Render environment variables, production secrets, deployment services, Docker image packaging, staging deployment, or backup/restore. Those remain ENG-099 and later.
