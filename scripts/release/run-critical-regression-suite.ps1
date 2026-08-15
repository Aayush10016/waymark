$ErrorActionPreference = "Stop"

.\mvnw.cmd verify
corepack pnpm verify
corepack pnpm --filter @trail/extension lint
corepack pnpm --filter @trail/extension typecheck
corepack pnpm --filter @trail/extension test
corepack pnpm --filter @trail/extension validate:manifest
corepack pnpm --filter @trail/extension build
corepack pnpm --filter @trail/extension webstore:package
corepack pnpm --filter @trail/extension webstore:validate
corepack pnpm openapi:check
docker build -f apps/backend/Dockerfile -t trail-backend:release-regression .

Write-Output "Trail critical release regression suite passed."
