# TypeScript Toolchain

Status: ENG-003 baseline.

## Pinned versions

| Tool | Version | Enforcement |
|---|---:|---|
| Node.js | 24.18.0 | `.tool-versions`, root `package.json` engines |
| pnpm | 11.10.0 | `.tool-versions`, root `packageManager`, root `package.json` engines |
| TypeScript | 5.9.3 | root `package.json` dev dependency |
| React | 19.2.7 | extension package dependencies |
| React DOM | 19.2.7 | extension package dependencies |
| Vite | 8.1.4 | extension package dependency |
| Vitest | 4.1.10 | root dev dependency |
| ESLint | 9.39.4 | root dev dependency |
| TypeScript ESLint | 8.63.0 | root dev dependency |
| OpenAPI TypeScript | 7.13.0 | root dev dependency |
| Redocly CLI | 2.38.0 | root dev dependency |

## Lock strategy

- `pnpm-lock.yaml` is committed and authoritative for TypeScript dependencies.
- Root `.npmrc` enables strict package-manager, peer-dependency, and engine checks.
- Workspace package versions are exact, not ranges.
- TypeScript remains on the 5.9 stable documentation line for this foundation sprint; moving to a new compiler major requires a scoped engineering issue.

## Supported Chrome versions

Trail v1.1 supports the current and previous stable desktop Chrome lines. At implementation time this is Chrome `150` and `149`; the local validation browser was Chrome `150.0.7871.47`.

Extension `minimum_chrome_version` is pinned to `149`.

