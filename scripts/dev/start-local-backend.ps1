param(
    [ValidateSet("local", "brevo")]
    [string] $EmailProvider = "brevo",

    [string] $TrailHome = (Join-Path ([Environment]::GetFolderPath("UserProfile")) ".trail"),

    [switch] $ValidateOnly
)

$ErrorActionPreference = "Stop"

$repoRoot = Resolve-Path (Join-Path $PSScriptRoot "..\..")
$secretRoot = Join-Path $TrailHome "secrets"
$configRoot = Join-Path $TrailHome "config"
$localConfigFile = Join-Path $configRoot "local-backend.env"
$privateKeyFile = Join-Path $secretRoot "jwt-private.pem"
$publicKeyFile = Join-Path $secretRoot "jwt-public.pem"
$brevoApiKeyFile = Join-Path $secretRoot "brevo-api-key.txt"
$dockerEnvFile = Join-Path $repoRoot "infra\docker\.env"
$privateKeyBeginMarker = "-----BEGIN " + "PRIVATE KEY-----"
$privateKeyEndMarker = "-----END " + "PRIVATE KEY-----"
$publicKeyBeginMarker = "-----BEGIN " + "PUBLIC KEY-----"
$publicKeyEndMarker = "-----END " + "PUBLIC KEY-----"

function Read-RequiredSecretFile {
    param(
        [string] $Path,
        [string] $Description
    )

    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) {
        throw "Missing required local secret file for $Description at $Path."
    }

    $value = Get-Content -LiteralPath $Path -Raw
    if ([string]::IsNullOrWhiteSpace($value)) {
        throw "Required local secret file for $Description is empty at $Path."
    }
    return $value
}

function Assert-PemShape {
    param(
        [string] $Value,
        [string] $BeginMarker,
        [string] $EndMarker,
        [string] $Description
    )

    $stripped = $Value.Trim()
    if (-not $stripped.StartsWith($BeginMarker) -or -not $stripped.EndsWith($EndMarker)) {
        throw "$Description must be PEM encoded with $BeginMarker and $EndMarker boundaries."
    }
}

function Read-KeyValueFile {
    param([string] $Path)

    $settings = @{}
    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) {
        return $settings
    }

    Get-Content -LiteralPath $Path | ForEach-Object {
        $line = $_.Trim()
        if ($line -and -not $line.StartsWith("#")) {
            $parts = $line.Split("=", 2)
            if ($parts.Length -ne 2 -or [string]::IsNullOrWhiteSpace($parts[0])) {
                throw "Invalid key/value line in $Path."
            }
            $settings[$parts[0].Trim()] = $parts[1].Trim()
        }
    }

    return $settings
}

function Set-EnvironmentIfUnset {
    param(
        [string] $Name,
        [string] $Value
    )

    if ([string]::IsNullOrWhiteSpace([Environment]::GetEnvironmentVariable($Name, "Process")) -and
        -not [string]::IsNullOrWhiteSpace($Value)) {
        [Environment]::SetEnvironmentVariable($Name, $Value, "Process")
    }
}

function Assert-NoSecretConfigKeys {
    param([hashtable] $Settings)

    $disallowed = @(
        "TRAIL_JWT_PRIVATE_KEY_PEM",
        "TRAIL_JWT_PUBLIC_KEY_PEM",
        "TRAIL_IDENTITY_EMAIL_BREVO_API_KEY",
        "TRAIL_DATABASE_PASSWORD"
    )

    foreach ($key in $disallowed) {
        if ($Settings.ContainsKey($key)) {
            throw "$localConfigFile must not contain secret key $key. Store secrets under $secretRoot instead."
        }
    }
}

function Apply-LocalConfiguration {
    $settings = Read-KeyValueFile -Path $localConfigFile
    Assert-NoSecretConfigKeys -Settings $settings

    Set-EnvironmentIfUnset -Name "TRAIL_IDENTITY_EMAIL_FROM_ADDRESS" -Value $settings["TRAIL_IDENTITY_EMAIL_FROM_ADDRESS"]
    Set-EnvironmentIfUnset -Name "TRAIL_IDENTITY_EMAIL_FROM_NAME" -Value $settings["TRAIL_IDENTITY_EMAIL_FROM_NAME"]
    Set-EnvironmentIfUnset -Name "TRAIL_IDENTITY_VERIFICATION_DESTINATION" -Value $settings["TRAIL_IDENTITY_VERIFICATION_DESTINATION"]
    Set-EnvironmentIfUnset -Name "TRAIL_IDENTITY_PASSWORD_RESET_DESTINATION" -Value $settings["TRAIL_IDENTITY_PASSWORD_RESET_DESTINATION"]
    Set-EnvironmentIfUnset -Name "TRAIL_JWT_KEY_ID" -Value $settings["TRAIL_JWT_KEY_ID"]
    Set-EnvironmentIfUnset -Name "TRAIL_JWT_ISSUER" -Value $settings["TRAIL_JWT_ISSUER"]
    Set-EnvironmentIfUnset -Name "TRAIL_JWT_AUDIENCE" -Value $settings["TRAIL_JWT_AUDIENCE"]
}

function Apply-DockerDatabaseConfiguration {
    $settings = Read-KeyValueFile -Path $dockerEnvFile

    $database = if ($settings["TRAIL_POSTGRES_DB"]) { $settings["TRAIL_POSTGRES_DB"] } else { "trail" }
    $user = if ($settings["TRAIL_POSTGRES_USER"]) { $settings["TRAIL_POSTGRES_USER"] } else { "trail" }
    $password = if ($settings["TRAIL_POSTGRES_PASSWORD"]) { $settings["TRAIL_POSTGRES_PASSWORD"] } else { "trail_local_password" }
    $port = if ($settings["TRAIL_POSTGRES_PORT"]) { $settings["TRAIL_POSTGRES_PORT"] } else { "5432" }

    [Environment]::SetEnvironmentVariable("TRAIL_DATABASE_URL", "jdbc:postgresql://localhost:$port/$database", "Process")
    [Environment]::SetEnvironmentVariable("TRAIL_DATABASE_USERNAME", $user, "Process")
    [Environment]::SetEnvironmentVariable("TRAIL_DATABASE_PASSWORD", $password, "Process")
}

$privateKeyPem = Read-RequiredSecretFile -Path $privateKeyFile -Description "JWT private key"
$publicKeyPem = Read-RequiredSecretFile -Path $publicKeyFile -Description "JWT public key"

Assert-PemShape `
    -Value $privateKeyPem `
    -BeginMarker $privateKeyBeginMarker `
    -EndMarker $privateKeyEndMarker `
    -Description "JWT private key"
Assert-PemShape `
    -Value $publicKeyPem `
    -BeginMarker $publicKeyBeginMarker `
    -EndMarker $publicKeyEndMarker `
    -Description "JWT public key"

Apply-LocalConfiguration

[Environment]::SetEnvironmentVariable("TRAIL_JWT_PRIVATE_KEY_PEM", $privateKeyPem, "Process")
[Environment]::SetEnvironmentVariable("TRAIL_JWT_PUBLIC_KEY_PEM", $publicKeyPem, "Process")
[Environment]::SetEnvironmentVariable("TRAIL_IDENTITY_EMAIL_PROVIDER", $EmailProvider, "Process")
Set-EnvironmentIfUnset -Name "TRAIL_JWT_KEY_ID" -Value "local"
Set-EnvironmentIfUnset -Name "TRAIL_JWT_ISSUER" -Value "trail-local"
Set-EnvironmentIfUnset -Name "TRAIL_JWT_AUDIENCE" -Value "trail-extension"
Set-EnvironmentIfUnset -Name "SPRING_FLYWAY_ENABLED" -Value "true"

if ($EmailProvider -eq "brevo") {
    $brevoApiKey = (Read-RequiredSecretFile -Path $brevoApiKeyFile -Description "Brevo API key").Trim()
    if ([string]::IsNullOrWhiteSpace($brevoApiKey)) {
        throw "Required local secret file for Brevo API key is empty at $brevoApiKeyFile."
    }
    [Environment]::SetEnvironmentVariable("TRAIL_IDENTITY_EMAIL_BREVO_API_KEY", $brevoApiKey, "Process")

    if ([string]::IsNullOrWhiteSpace([Environment]::GetEnvironmentVariable("TRAIL_IDENTITY_EMAIL_FROM_ADDRESS", "Process"))) {
        throw "TRAIL_IDENTITY_EMAIL_FROM_ADDRESS is required when EmailProvider is brevo. Set it in $localConfigFile or in the current process environment."
    }
}

Apply-DockerDatabaseConfiguration

if ($ValidateOnly) {
    if ([Environment]::GetEnvironmentVariable("TRAIL_JWT_PRIVATE_KEY_PEM", "Process") -ne $privateKeyPem) {
        throw "JWT private key was not loaded into the process environment."
    }
    if ([Environment]::GetEnvironmentVariable("TRAIL_JWT_PUBLIC_KEY_PEM", "Process") -ne $publicKeyPem) {
        throw "JWT public key was not loaded into the process environment."
    }
    Write-Output "Local backend configuration validation passed."
    exit 0
}

& (Join-Path $PSScriptRoot "start-local-infra.ps1")
if ($LASTEXITCODE -ne 0) {
    throw "start-local-infra.ps1 failed with exit code $LASTEXITCODE."
}

& (Join-Path $PSScriptRoot "verify-local-database.ps1")
if ($LASTEXITCODE -ne 0) {
    throw "verify-local-database.ps1 failed with exit code $LASTEXITCODE."
}

& (Join-Path $repoRoot "mvnw.cmd") -pl apps/backend spring-boot:run
exit $LASTEXITCODE
