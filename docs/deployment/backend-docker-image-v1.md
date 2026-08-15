# Backend Docker Image v1

ENG-099 defines Trail's production backend image contract.

## Image contract

- Build target: `apps/backend/Dockerfile`.
- Build style: multi-stage OCI image.
- Builder base: pinned Maven 3.9.16 with Eclipse Temurin Java 17.
- Runtime base: pinned Eclipse Temurin Java 17 JRE.
- Runtime user: non-root `trail` user with UID/GID `10001`.
- Runtime port: `8080`.
- Health path: `/actuator/health/readiness`.
- Shutdown: SIGTERM with Spring Boot graceful shutdown enabled.
- Secrets: no secrets, `.env` files, local build outputs, or dependency caches are copied from the host context.

## Build

```powershell
docker build -f apps/backend/Dockerfile -t trail-backend:local .
```

## Runtime smoke check

```powershell
docker run --rm -p 18080:8080 `
  -e TRAIL_BACKGROUND_JOBS_SCHEDULER_ENABLED=false `
  --name trail-backend-smoke `
  trail-backend:local
```

Then verify:

```powershell
Invoke-WebRequest http://127.0.0.1:18080/actuator/health/readiness
```

## Image scan

Run an available local scanner before release:

```powershell
docker scout quickview trail-backend:local
```

If Docker Scout is unavailable, scan the image with the CI-approved OCI scanner before promotion. Critical or high findings in runtime packages require explicit remediation or accepted risk.

## Deferred

ENG-099 does not configure Render services, production secrets, environment variables, regions, autoscaling, database URLs, or release promotion. Those remain ENG-100 and later.
