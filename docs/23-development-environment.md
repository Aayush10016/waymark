# Development Environment

Status: frozen toolchain baseline
Issue: ENG-002
Last verified: 2026-07-05

## Required versions

| Tool | Required version | Authority |
|---|---|---|
| Java | Eclipse Temurin 17.0.19+10 | `.tool-versions` and ADR-0001 |
| Maven | 3.9.16 | Maven Wrapper and ADR-0002 |
| Spring Boot | 3.5.15 | ADR-0027 |

Java source and bytecode must target Java 17. Spring Boot 4.x is unsupported by Trail Architecture v1.1 and requires a superseding ADR.

## Clean-machine setup

1. Install Eclipse Temurin JDK 17.0.19+10 from Eclipse Adoptium, or use a version manager that reads `.tool-versions`.
2. Set `JAVA_HOME` to that JDK and ensure its `bin` directory precedes other Java installations on `PATH`.
3. Do not install Maven globally for Trail. The committed Wrapper downloads and verifies Maven 3.9.16.
4. From the repository root, verify the toolchain:

   Windows:

   ```powershell
   .\mvnw.cmd --version
   ```

   macOS or Linux:

   ```sh
   chmod +x mvnw
   ./mvnw --version
   ```

The output must report Maven 3.9.16, Java 17.0.19, and Eclipse Adoptium/Temurin. A different Java patch or vendor is not the reproducible ENG-002 baseline even when it can launch Maven.

## Enforcement boundary

- `.tool-versions` pins the developer JDK distribution and patch.
- Maven Wrapper pins Maven 3.9.16 and verifies the downloaded distribution using the committed SHA-256 checksum.
- ADR-0027 prohibits Spring Boot 4.x. ENG-005 must encode Spring Boot 3.5.15 and Java/Maven Enforcer rules when it creates the backend POM; no POM is authorized by ENG-002.
- ENG-010 must run the same pinned toolchain in CI. Container-image pinning remains with its deployment issue.

Do not weaken or override these versions locally. Patch upgrades require an intentional ENG issue, updated checksums/version files, and review; Spring Boot major upgrades require a superseding ADR.

## Troubleshooting

If the Wrapper reports the wrong Java runtime, correct `JAVA_HOME` and reopen the terminal. If Maven distribution verification fails, do not remove `distributionSha256Sum`; remove the cached failed download under the Maven Wrapper cache and retry from a trusted network.

## Local identity email flow

Trail does not require a purchased domain for local verification or password-reset testing.

Default local identity destinations are:

| Flow | Default destination |
|---|---|
| Email verification | `http://localhost:8080/verify-email` |
| Password reset | `http://localhost:8080/reset-password` |

For local development:

1. Start PostgreSQL/pgvector from `infra/docker`.
2. Start the backend on port `8080`.
3. Use `TRAIL_IDENTITY_EMAIL_PROVIDER=local` to inspect generated email messages in tests, or configure `TRAIL_IDENTITY_EMAIL_PROVIDER=brevo` with a verified sender for live delivery.
4. Register an account through `POST /api/v1/registrations`.
5. Open the verification URL from the delivered email. The token is carried in the URL fragment (`#token=...`), the page submits it through `POST /api/v1/email-verifications`, removes the token from the address bar, and does not store it.
6. Request password reset through `POST /api/v1/password-reset-requests`.
7. Open the reset URL from the delivered email. The token is carried in the URL fragment (`#token=...`), the page reads it in memory, removes it from the address bar, accepts a new password, and submits `POST /api/v1/password-resets`.

Staging and production must override:

- `TRAIL_IDENTITY_VERIFICATION_DESTINATION`
- `TRAIL_IDENTITY_PASSWORD_RESET_DESTINATION`

Those values must be reachable HTTPS URLs owned by the deployment environment. `app.trail.local` is not a public production domain.

## One-command local backend startup

Trail provides a Windows PowerShell launcher for local backend development:

```powershell
.\scripts\dev\start-local-backend.ps1
```

The launcher extends the existing development-script model. It starts and verifies the local Docker PostgreSQL/pgvector infrastructure through `scripts/dev/start-local-infra.ps1` and `scripts/dev/verify-local-database.ps1`, enables Flyway in the launched process for local schema creation, then starts Spring Boot with the Maven Wrapper.

### First-time local secrets

Create local secret/config directories outside the repository:

```powershell
New-Item -ItemType Directory -Force "$HOME\.trail\secrets"
New-Item -ItemType Directory -Force "$HOME\.trail\config"
```

Required files:

| File | Purpose | Repository status |
|---|---|---|
| `$HOME\.trail\secrets\jwt-private.pem` | Local RS256 JWT signing key, PKCS#8 PEM | Never committed |
| `$HOME\.trail\secrets\jwt-public.pem` | Local RS256 JWT verification key, X.509 PEM | Never committed |
| `$HOME\.trail\secrets\brevo-api-key.txt` | Local Brevo transactional email key when `-EmailProvider brevo` is used | Never committed |

Generate the JWT key pair once and reuse it across restarts:

```powershell
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out "$HOME\.trail\secrets\jwt-private.pem"
openssl rsa -pubout -in "$HOME\.trail\secrets\jwt-private.pem" -out "$HOME\.trail\secrets\jwt-public.pem"
```

Store the Brevo key in `$HOME\.trail\secrets\brevo-api-key.txt`. Do not store JWT PEMs or Brevo keys in `infra/docker/.env`, repository docs, shell scripts, committed examples, or test fixtures.

For Brevo mode, configure the verified sender address as non-secret local configuration:

```powershell
Set-Content -LiteralPath "$HOME\.trail\config\local-backend.env" -Value "TRAIL_IDENTITY_EMAIL_FROM_ADDRESS=<verified-sender-address>"
```

Optional non-secret values in `$HOME\.trail\config\local-backend.env`:

- `TRAIL_IDENTITY_EMAIL_FROM_NAME`
- `TRAIL_IDENTITY_VERIFICATION_DESTINATION`
- `TRAIL_IDENTITY_PASSWORD_RESET_DESTINATION`
- `TRAIL_JWT_KEY_ID`
- `TRAIL_JWT_ISSUER`
- `TRAIL_JWT_AUDIENCE`

The launcher rejects local config entries for `TRAIL_JWT_PRIVATE_KEY_PEM`, `TRAIL_JWT_PUBLIC_KEY_PEM`, `TRAIL_IDENTITY_EMAIL_BREVO_API_KEY`, and `TRAIL_DATABASE_PASSWORD`.

### Daily startup

Prerequisite: Docker Desktop must be running.

Start the backend with live Brevo identity email delivery:

```powershell
.\scripts\dev\start-local-backend.ps1
```

Start the backend with local in-memory identity email delivery:

```powershell
.\scripts\dev\start-local-backend.ps1 -EmailProvider local
```

Validate local secret/config loading without starting Docker or Spring Boot:

```powershell
.\scripts\dev\start-local-backend.ps1 -ValidateOnly
```

Stop Spring Boot with `Ctrl+C`. Stop local PostgreSQL separately:

```powershell
.\scripts\dev\stop-local-infra.ps1
```

### Rotation

Do not regenerate JWT keys for normal restarts. Deliberate JWT key rotation changes the local signing key pair and affects existing JWT access-token validation; after rotation, sign in again to receive tokens signed by the new key.

Brevo key replacement only requires updating `$HOME\.trail\secrets\brevo-api-key.txt` and restarting the backend. No repository file should change when rotating local secrets.

If a Brevo key is accidentally pasted into a repository file, terminal transcript, issue, screenshot, or shared document, revoke it in Brevo and replace `$HOME\.trail\secrets\brevo-api-key.txt` before using live delivery again.
