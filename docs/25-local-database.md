# Local Database

Status: ENG-009 baseline.

## Purpose

Trail uses PostgreSQL 17 with pgvector 0.8.2 as the local and integration-test database foundation.

ENG-009 only establishes the database infrastructure baseline. It does not add application schemas, Flyway migrations, repositories, authentication, APIs, AI, or capture behavior.

## Prerequisites

- Docker Desktop installed and running.
- Docker Compose available as `docker compose`.
- Maven Wrapper available through `mvnw.cmd` on Windows or `./mvnw` on macOS/Linux.

Verify Docker:

```powershell
docker --version
docker compose version
```

## Start local database

```powershell
.\scripts\dev\start-local-infra.ps1
```

The script creates `infra/docker/.env` from `infra/docker/.env.example` if needed, then starts the Compose stack.

## Verify local database

```powershell
.\scripts\dev\verify-local-database.ps1
```

Expected result:

```text
PostgreSQL 17.x with pgvector 0.8.2 is healthy.
```

## Stop local database

```powershell
.\scripts\dev\stop-local-infra.ps1
```

## Reset local database

This deletes the persistent Docker volume and all local database data.

```powershell
.\scripts\dev\reset-local-infra.ps1
```

## Environment variables

Defined in `infra/docker/.env`.

| Variable | Default | Purpose |
|---|---:|---|
| `TRAIL_POSTGRES_DB` | `trail` | Local database name |
| `TRAIL_POSTGRES_USER` | `trail` | Local database user |
| `TRAIL_POSTGRES_PASSWORD` | `trail_local_password` | Local-only database password |
| `TRAIL_POSTGRES_PORT` | `5432` | Host port mapped to PostgreSQL |

## Validation commands

Backend Testcontainers baseline:

```powershell
.\mvnw.cmd -pl apps/backend -Dtest=PostgresPgvectorContainerTest test
```

Full Maven verification:

```powershell
.\mvnw.cmd verify
```

## Troubleshooting

- If the database port is already in use, change `TRAIL_POSTGRES_PORT` in `infra/docker/.env`.
- If `verify-local-database.ps1` reports no pgvector extension, run `.\scripts\dev\reset-local-infra.ps1` and start again. PostgreSQL init scripts run only on first volume initialization.
- If Docker commands fail, start Docker Desktop and retry.
- If Testcontainers fails to connect to Docker, verify Docker Desktop is running and available to the current shell.
