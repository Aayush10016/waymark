# Waymark

Waymark is a VS Code extension that keeps AI-assisted development aligned with your project's explicit engineering rules and decisions.

It gives your project a local source of truth, checks changed code against that truth, and lets you ask grounded questions about selected code when you choose to use AI.

## Why Waymark exists

AI coding tools move fast. That speed is useful, but it also makes it easy to drift away from decisions your project has already made:

- "Do not add Redis."
- "Use PostgreSQL for persistence."
- "All backend changes must preserve clean architecture boundaries."
- "Never bypass the auth service."
- "Use this ADR as the source of truth."

Waymark keeps those rules and decisions visible inside VS Code so developers and AI agents can stay on the same path.

## What Waymark does

- Stores project rules and decisions as plain Markdown.
- Shows project memory in the VS Code Activity Bar.
- Checks changed files with a deterministic local Guardian.
- Reports rule conflicts as VS Code diagnostics.
- Builds bounded code context for selected code.
- Provides optional grounded AI assistance through NVIDIA when explicitly requested.
- Proposes safe remediation for Guardian findings without auto-editing code.

## What Waymark does not do

- It does not upload your repository in the background.
- It does not run background AI.
- It does not create embeddings or a vector database.
- It does not perform repository-wide source indexing.
- It does not mutate Git history.
- It does not auto-apply AI edits.
- It does not require a backend service.

## Install

Install Waymark from the Visual Studio Marketplace:

```text
Waymark-Dev.waymark
```

Marketplace URL:

```text
https://marketplace.visualstudio.com/items?itemName=Waymark-Dev.waymark
```

## Getting started

1. Install Waymark.
2. Open a project in VS Code.
3. Run `Waymark: Initialize Project`.
4. Create a project constitution.
5. Add rules and decisions.
6. Start coding.
7. Review Guardian findings in VS Code diagnostics and the Waymark Activity Bar.

The fastest selected-code workflow:

1. Select code in the editor.
2. Right-click.
3. Open the `Waymark` submenu.
4. Choose `Explain Selected Code`, `Ask About Selected Code`, `Check Against Project Rules`, or `Show Code Context`.

## Local project storage

Waymark stores project memory in a local `.trail/` directory for compatibility with existing projects:

```text
.trail/
  project.json
  constitution.md
  rules/
  decisions/
```

The `.trail/` directory name is intentional backward compatibility. It is not a branding error and should not be renamed without a migration plan.

## Core features

### Project Rules

Create explicit local rules for architecture, security, dependencies, coding conventions, and team agreements.

Rules are stored under:

```text
.trail/rules/
```

### Project Decisions

Record architectural decisions as local ADR-style Markdown files.

Decisions are stored under:

```text
.trail/decisions/
```

### Guardian

Guardian checks relevant changed files against active project rules and decisions.

Guardian is deterministic and local. It does not use AI and does not upload code.

### Project Memory

Project Memory is rebuilt from local Markdown and bounded read-only Git metadata:

- `.trail/project.json`
- `.trail/constitution.md`
- `.trail/rules/*.md`
- `.trail/decisions/*.md`
- Git status and recent commit metadata

Retrieval is deterministic lexical retrieval. Waymark does not use embeddings or vector search.

### Code Context

Waymark can build a local context package for selected code, including:

- selected code
- bounded surrounding code
- file metadata
- relevant project rules
- relevant project decisions
- read-only Git context

Use `Waymark: Show Code Context` to inspect what Waymark knows before asking AI.

### Optional NVIDIA AI

Waymark AI features are explicit and user-triggered.

If configured, Waymark can ask a NVIDIA-hosted model to explain selected code, answer a selected-code question, or propose a guarded remediation for a deterministic Guardian finding.

Waymark does not persist or display your API key.

## NVIDIA setup

Waymark reads NVIDIA configuration from the VS Code extension host process environment:

```text
NVIDIA_API_KEY
NVIDIA_MODEL
```

Windows PowerShell example:

```powershell
[Environment]::SetEnvironmentVariable("NVIDIA_API_KEY", "YOUR_KEY", "User")
[Environment]::SetEnvironmentVariable("NVIDIA_MODEL", "meta/llama-3.1-8b-instruct", "User")
```

Restart VS Code after changing user-level environment variables.

Optional overrides:

```powershell
[Environment]::SetEnvironmentVariable("NVIDIA_BASE_URL", "https://integrate.api.nvidia.com/v1", "User")
[Environment]::SetEnvironmentVariable("NVIDIA_TIMEOUT_MS", "30000", "User")
[Environment]::SetEnvironmentVariable("NVIDIA_MAX_RETRIES", "2", "User")
[Environment]::SetEnvironmentVariable("NVIDIA_MAX_TOKENS", "900", "User")
[Environment]::SetEnvironmentVariable("NVIDIA_TEMPERATURE", "0.2", "User")
```

## Commands

Project setup:

- `Waymark: Initialize Project`
- `Waymark: Select Workspace Folder`
- `Waymark: Create Constitution`
- `Waymark: Open Constitution`

Rules and decisions:

- `Waymark: Add Rule`
- `Waymark: Delete Rule`
- `Waymark: Import Architecture Document`
- `Waymark: Add Decision`

Guardian:

- `Waymark: Check Against Project Rules`
- `Waymark: Check Current Changes`
- `Waymark: Show Guardian Finding`

Project Memory and Git context:

- `Waymark: Open Project Memory`
- `Waymark: Search Project Memory`
- `Waymark: Refresh Project Memory`
- `Waymark: Show Memory Result Details`
- `Waymark: Refresh Git Context`
- `Waymark: Open Changed File`
- `Waymark: View File Diff`
- `Waymark: Show Commit Details`

Code Context and AI:

- `Waymark: Show Code Context`
- `Waymark: Explain Selected Code`
- `Waymark: Ask About Selected Code`
- `Waymark: Show AI Configuration`

## Development

Install dependencies from the repository root:

```bash
corepack pnpm install
```

Run the VS Code extension checks:

```bash
corepack pnpm --filter ./apps/vscode-extension verify
```

Run the full workspace verification:

```bash
corepack pnpm verify
```

Build a local VSIX:

```bash
corepack pnpm --filter ./apps/vscode-extension package:vsix
```

## Extension development host

1. Open this repository in VS Code.
2. Press F5.
3. VS Code builds the extension.
4. A separate Extension Development Host window opens with Waymark loaded.

## Repository layout

```text
apps/vscode-extension/   Waymark VS Code extension
apps/extension/          Legacy browser-extension workspace retained in the monorepo
packages/                Shared TypeScript packages
docs/                    Historical product and architecture documentation
scripts/                 Repository validation scripts
```

## Privacy and security

- Project rules and decisions are local Markdown files.
- Guardian checks are local and deterministic.
- Automatic Guardian does not call NVIDIA.
- AI requests happen only after explicit user action.
- AI requests may send selected/contextual code and project evidence to the configured NVIDIA endpoint.
- API keys are not persisted by Waymark.
- Waymark does not call a backend service.

## License

Waymark is licensed under the MIT License.
