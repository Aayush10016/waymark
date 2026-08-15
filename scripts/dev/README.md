# Development Scripts

Developer helper scripts live here when repeated workflows justify them. Scripts are thin wrappers around native tools and fail loudly instead of hiding Maven, pnpm, Docker, or Spring Boot output.

## Local infrastructure

Start PostgreSQL/pgvector:

```powershell
.\scripts\dev\start-local-infra.ps1
```

Verify PostgreSQL/pgvector:

```powershell
.\scripts\dev\verify-local-database.ps1
```

Stop PostgreSQL/pgvector:

```powershell
.\scripts\dev\stop-local-infra.ps1
```

Reset PostgreSQL/pgvector, including the local Docker volume:

```powershell
.\scripts\dev\reset-local-infra.ps1
```

## First-time local backend setup

Create a local Trail secret directory outside the repository:

```powershell
New-Item -ItemType Directory -Force "$HOME\.trail\secrets"
New-Item -ItemType Directory -Force "$HOME\.trail\config"
```

Generate the JWT RSA key pair once and reuse it across backend restarts:

```powershell
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out "$HOME\.trail\secrets\jwt-private.pem"
openssl rsa -pubout -in "$HOME\.trail\secrets\jwt-private.pem" -out "$HOME\.trail\secrets\jwt-public.pem"
```

Store the Brevo API key outside the repository:

```powershell
Set-Content -NoNewline -LiteralPath "$HOME\.trail\secrets\brevo-api-key.txt" -Value "<paste-key-in-your-terminal-only>"
```

Do not commit, copy into docs, or paste secret values into issue comments, logs, screenshots, `infra/docker/.env`, or shell history you plan to share.

For Brevo mode, configure the verified sender address in a non-secret local config file:

```powershell
Set-Content -LiteralPath "$HOME\.trail\config\local-backend.env" -Value "TRAIL_IDENTITY_EMAIL_FROM_ADDRESS=<verified-sender-address>"
```

Optional non-secret local values in `$HOME\.trail\config\local-backend.env`:

```text
TRAIL_IDENTITY_EMAIL_FROM_NAME=Trail
TRAIL_IDENTITY_VERIFICATION_DESTINATION=http://localhost:8080/verify-email
TRAIL_IDENTITY_PASSWORD_RESET_DESTINATION=http://localhost:8080/reset-password
TRAIL_JWT_KEY_ID=local
TRAIL_JWT_ISSUER=trail-local
TRAIL_JWT_AUDIENCE=trail-extension
```

The local config file must not contain JWT PEMs, Brevo API keys, or database passwords. The startup script rejects those keys.

## Normal daily backend startup

Prerequisite: Docker Desktop must be running.

From the repository root:

```powershell
.\scripts\dev\start-local-backend.ps1
```

The script:

- validates `$HOME\.trail\secrets\jwt-private.pem`;
- validates `$HOME\.trail\secrets\jwt-public.pem`;
- validates `$HOME\.trail\secrets\brevo-api-key.txt` when Brevo mode is selected;
- loads secret values only into the backend process environment;
- reads non-secret local settings from `$HOME\.trail\config\local-backend.env` when present;
- enables Flyway in the launched process so a clean local database receives the current schema;
- reuses `start-local-infra.ps1` and `verify-local-database.ps1`;
- starts the backend through `.\mvnw.cmd -pl apps/backend spring-boot:run`.

Use local email mode when live Brevo delivery is not needed:

```powershell
.\scripts\dev\start-local-backend.ps1 -EmailProvider local
```

Stop the backend with `Ctrl+C`. Stop PostgreSQL/pgvector separately when needed:

```powershell
.\scripts\dev\stop-local-infra.ps1
```

## Validate local backend configuration without starting services

```powershell
.\scripts\dev\start-local-backend.ps1 -ValidateOnly
```

Validation confirms required local files and process environment loading without printing secret values.

## Secret rotation

JWT keys should not be regenerated on every restart. Regenerating them deliberately changes the local signing/verification key pair and invalidates or affects existing JWT access-token validation.

To rotate local JWT keys deliberately:

1. Stop the backend.
2. Replace both `$HOME\.trail\secrets\jwt-private.pem` and `$HOME\.trail\secrets\jwt-public.pem`.
3. Restart with `.\scripts\dev\start-local-backend.ps1`.
4. Log in again to receive tokens signed by the new key.

To rotate the local Brevo key, replace only `$HOME\.trail\secrets\brevo-api-key.txt` and restart the backend. Do not put the Brevo key in repository files or `infra/docker/.env`.

If a Brevo key is ever pasted into a repository file, terminal transcript, issue, screenshot, or shared document, treat it as compromised: revoke it in Brevo, create a replacement key, update `$HOME\.trail\secrets\brevo-api-key.txt`, and restart the backend.
