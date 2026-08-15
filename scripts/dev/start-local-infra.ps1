$ErrorActionPreference = "Stop"

$repoRoot = Resolve-Path (Join-Path $PSScriptRoot "..\..")
$envFile = Join-Path $repoRoot "infra\docker\.env"
$exampleEnvFile = Join-Path $repoRoot "infra\docker\.env.example"
$composeFile = Join-Path $repoRoot "infra\docker\compose.yaml"
. (Join-Path $PSScriptRoot "docker-helpers.ps1")

if (-not (Test-Path $envFile)) {
    Copy-Item -LiteralPath $exampleEnvFile -Destination $envFile
}

Assert-DockerRunning
docker compose --env-file $envFile -f $composeFile up -d
if ($LASTEXITCODE -ne 0) {
    throw "docker compose up failed with exit code $LASTEXITCODE."
}
