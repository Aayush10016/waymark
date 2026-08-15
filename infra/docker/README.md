# Docker

Local Docker infrastructure for Trail development.

## Scope

ENG-009 provides only local PostgreSQL 17 with pgvector 0.8.2. It does not create a backend image, application schema, Flyway migration, production deployment, authentication, APIs, or business data.

## Prerequisites

- Docker Desktop running.
- Docker Compose available through `docker compose`.
- No local PostgreSQL process already bound to the configured port.

Verify:

```powershell
docker --version
docker compose version
```

## Environment variables

Copy the example file before local customization:

```powershell
Copy-Item infra/docker/.env.example infra/docker/.env
```

| Variable | Default | Purpose |
|---|---:|---|
| `TRAIL_POSTGRES_DB` | `trail` | Local database name |
| `TRAIL_POSTGRES_USER` | `trail` | Local database user |
| `TRAIL_POSTGRES_PASSWORD` | `trail_local_password` | Local-only password |
| `TRAIL_POSTGRES_PORT` | `5432` | Host port mapped to container port `5432` |

The local password is not a production secret. Do not reuse it outside local development.

`infra/docker/.env` is for local Docker database settings only. Do not place JWT PEMs, Brevo API keys, application API keys, or production secrets in this file. Local backend secrets are loaded at runtime from `$HOME\.trail\secrets` by `scripts/dev/start-local-backend.ps1`.

## Start

```powershell
.\scripts\dev\start-local-infra.ps1
```

Equivalent direct command:

```powershell
docker compose --env-file infra/docker/.env -f infra/docker/compose.yaml up -d
```

## Verify

```powershell
.\scripts\dev\verify-local-database.ps1
```

The verification checks:

- container health;
- PostgreSQL major version `17`;
- pgvector extension version `0.8.2`;
- vector distance operator availability.

## Stop

```powershell
.\scripts\dev\stop-local-infra.ps1
```

Equivalent direct command:

```powershell
docker compose --env-file infra/docker/.env -f infra/docker/compose.yaml stop
```

## Reset

This deletes the persistent local PostgreSQL volume.

```powershell
.\scripts\dev\reset-local-infra.ps1
```

Equivalent direct command:

```powershell
docker compose --env-file infra/docker/.env -f infra/docker/compose.yaml down --volumes --remove-orphans
```

## Image pin

Compose uses:

```text
pgvector/pgvector:0.8.2-pg17@sha256:feb68f4f15446397d8cac7f4fe48fe4586de83160d1fc48b46283312d1a33966
```

This pins the manifest list for pgvector 0.8.2 on PostgreSQL 17. Platform-specific manifests observed during ENG-009 validation:

- linux/amd64: `sha256:e04af45eb526378554a24ed05b37d9ea56fd623feca9adf264d4f47d875c9a93`
- linux/arm64: `sha256:88b17bc7a7dd6ef73d46c1b15aaa5936d50acb24e4a78713e134c579bf359e25`

## Troubleshooting

- If port `5432` is already in use, change `TRAIL_POSTGRES_PORT` in `infra/docker/.env`.
- If the container is unhealthy, inspect logs with `docker logs trail-postgres`.
- If pgvector is missing after changing init SQL, reset the volume; PostgreSQL init scripts run only when the data directory is first created.
- If Docker is unavailable, start Docker Desktop and rerun the start command.
