param(
  [Parameter(Mandatory = $true)]
  [string] $BaseUrl
)

$ErrorActionPreference = "Stop"

function Invoke-TrailHealthCheck {
  param(
    [Parameter(Mandatory = $true)]
    [string] $Path
  )

  $uri = "$($BaseUrl.TrimEnd('/'))$Path"
  $response = Invoke-RestMethod -Uri $uri -Method Get -TimeoutSec 10
  if ($response.status -ne "UP") {
    throw "Expected $Path to return UP."
  }
}

Invoke-TrailHealthCheck -Path "/actuator/health/readiness"
Invoke-TrailHealthCheck -Path "/actuator/health/liveness"

try {
  Invoke-RestMethod -Uri "$($BaseUrl.TrimEnd('/'))/actuator/metrics" -Method Get -TimeoutSec 10 | Out-Null
  throw "Public metrics endpoint must not be exposed."
} catch {
  if ($_.Exception.Message -eq "Public metrics endpoint must not be exposed.") {
    throw
  }
}

Write-Output "Render staging health/privacy checks passed."
