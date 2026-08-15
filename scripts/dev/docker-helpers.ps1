function Assert-DockerRunning {
    docker info *> $null
    if ($LASTEXITCODE -ne 0) {
        throw "Docker Desktop is not running or Docker Engine is unavailable. Start Docker Desktop and retry."
    }
}
