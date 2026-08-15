# Trail Backend

Purpose: executable Spring Boot modular monolith for Trail.

Owner: Backend Owner.

Local verification:

```powershell
.\mvnw.cmd -pl apps/backend test
.\mvnw.cmd -pl apps/backend spring-boot:run
```

Local backend with persistent JWT/Brevo configuration:

```powershell
.\scripts\dev\start-local-backend.ps1
```

This command starts/verifies local PostgreSQL and runs Spring Boot through the Maven Wrapper. It reads JWT PEMs and the optional Brevo API key from `$HOME\.trail\secrets`; secrets are not stored in the repository or `infra/docker/.env`.

Local identity links:

- Email verification: `http://localhost:8080/verify-email`
- Password reset: `http://localhost:8080/reset-password`

Override `TRAIL_IDENTITY_VERIFICATION_DESTINATION` and `TRAIL_IDENTITY_PASSWORD_RESET_DESTINATION` for staging or production HTTPS destinations.

Boundaries:

- Java root package: `io.github.aayush10016.trail`.
- Capability packages compile, but business logic is intentionally absent in the foundation.
- No controllers, entities, repositories, authentication, database schema, capture logic, or AI code belongs to ENG-005.
