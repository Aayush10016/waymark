# Scripts

Scripts are thin wrappers for Maven, pnpm, and Docker commands. They must fail loudly and avoid hiding native tool behavior.

Development entry points:

- `scripts/dev/start-local-infra.ps1`: start local PostgreSQL/pgvector.
- `scripts/dev/verify-local-database.ps1`: verify local PostgreSQL/pgvector health and versions.
- `scripts/dev/start-local-backend.ps1`: load local runtime secrets from `$HOME\.trail\secrets`, start/verify local infrastructure, then run Spring Boot through the Maven Wrapper.
