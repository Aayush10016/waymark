$ErrorActionPreference = "Stop"

$repoRoot = Resolve-Path (Join-Path $PSScriptRoot "..\..")
$envFile = Join-Path $repoRoot "infra\docker\.env"
$composeFile = Join-Path $repoRoot "infra\docker\compose.yaml"
. (Join-Path $PSScriptRoot "docker-helpers.ps1")

Assert-DockerRunning
docker compose --env-file $envFile -f $composeFile stop
if ($LASTEXITCODE -ne 0) {
    throw "docker compose stop failed with exit code $LASTEXITCODE."
}
