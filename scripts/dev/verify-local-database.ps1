$ErrorActionPreference = "Stop"

$repoRoot = Resolve-Path (Join-Path $PSScriptRoot "..\..")
$envFile = Join-Path $repoRoot "infra\docker\.env"
$composeFile = Join-Path $repoRoot "infra\docker\compose.yaml"
. (Join-Path $PSScriptRoot "docker-helpers.ps1")

if (-not (Test-Path $envFile)) {
    throw "Missing infra/docker/.env. Run scripts/dev/start-local-infra.ps1 first."
}

$settings = @{}
Get-Content -LiteralPath $envFile | ForEach-Object {
    $line = $_.Trim()
    if ($line -and -not $line.StartsWith("#")) {
        $parts = $line.Split("=", 2)
        if ($parts.Length -eq 2) {
            $settings[$parts[0]] = $parts[1]
        }
    }
}

$database = $settings["TRAIL_POSTGRES_DB"]
$user = $settings["TRAIL_POSTGRES_USER"]
$password = $settings["TRAIL_POSTGRES_PASSWORD"]

if (-not $database -or -not $user -or -not $password) {
    throw "infra/docker/.env must define TRAIL_POSTGRES_DB, TRAIL_POSTGRES_USER, and TRAIL_POSTGRES_PASSWORD."
}

Assert-DockerRunning
docker compose --env-file $envFile -f $composeFile up -d
if ($LASTEXITCODE -ne 0) {
    throw "docker compose up failed with exit code $LASTEXITCODE."
}

$deadline = (Get-Date).AddSeconds(90)
do {
    $health = docker inspect trail-postgres --format "{{if .State.Health}}{{.State.Health.Status}}{{else}}none{{end}}"
    if ($LASTEXITCODE -ne 0) {
        throw "Failed to inspect trail-postgres health."
    }
    if ($health -eq "healthy") {
        break
    }
    Start-Sleep -Seconds 2
} while ((Get-Date) -lt $deadline)

if ($health -ne "healthy") {
    throw "trail-postgres did not become healthy within 90 seconds. Current status: $health"
}

$postgresVersion = docker compose --env-file $envFile -f $composeFile exec -T postgres `
    psql -U $user -d $database -tAc "select current_setting('server_version')"
if ($LASTEXITCODE -ne 0) {
    throw "Failed to query PostgreSQL version."
}

if (-not $postgresVersion.StartsWith("17.")) {
    throw "Expected PostgreSQL 17.x, got $postgresVersion"
}

$pgvectorVersion = docker compose --env-file $envFile -f $composeFile exec -T postgres `
    psql -U $user -d $database -tAc "select extversion from pg_extension where extname = 'vector'"
if ($LASTEXITCODE -ne 0) {
    throw "Failed to query pgvector version."
}

if ($pgvectorVersion -ne "0.8.2") {
    throw "Expected pgvector 0.8.2, got $pgvectorVersion"
}

$distance = docker compose --env-file $envFile -f $composeFile exec -T postgres `
    psql -U $user -d $database -tAc "select '[1,2,3]'::vector <-> '[1,2,4]'::vector"
if ($LASTEXITCODE -ne 0) {
    throw "Failed to run pgvector distance smoke query."
}

if ([double]$distance -ne 1.0) {
    throw "Expected vector distance 1.0, got $distance"
}

Write-Output "PostgreSQL $postgresVersion with pgvector $pgvectorVersion is healthy."
