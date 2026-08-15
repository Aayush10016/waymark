# Waymark

Waymark keeps AI-assisted development aligned with your project's explicit engineering rules and decisions. It maintains local project truth, checks code changes with a deterministic Guardian, and can optionally provide grounded NVIDIA AI assistance when you ask for it.

## Why Waymark?

AI coding agents can make large changes quickly, but they do not automatically know the engineering constraints your project has agreed to follow.

Waymark keeps those constraints visible inside VS Code. You define rules and decisions in plain Markdown, then Waymark checks relevant changed code against those explicit project requirements.

The goal is simple: AI agents can make changes, but Waymark continuously helps you see whether those changes still follow the project's path.

## Features

### Project Rules

Create and maintain explicit project rules such as required architecture boundaries, forbidden dependencies, security requirements, and coding conventions.

Rules are stored locally as Markdown files under `.trail/rules/`. The `.trail/` directory is the current local project-memory format.

### Project Decisions

Record architectural decisions as local ADR-style Markdown files under `.trail/decisions/`.

Waymark can list, create, open, and use accepted decisions as supporting evidence when checking code.

### Automatic Guardian

Waymark watches relevant changed files and runs deterministic rule checks locally.

Guardian does not use AI. It does not upload code. It reports potential project-rule conflicts with evidence from explicit rules, decisions, selected code, and changed-file context.

### Project Memory

Waymark builds an in-memory, rebuildable Project Memory from:

- `.trail/project.json`
- `.trail/constitution.md`
- `.trail/rules/*.md`
- `.trail/decisions/*.md`
- bounded read-only Git metadata

Project Memory uses deterministic lexical retrieval. It does not use embeddings, vector search, or repository-wide source indexing.

### Code Context

Waymark can prepare a local context package for selected code. The context includes the selection, bounded surrounding code, file metadata, relevant Project Memory evidence, and read-only Git evidence.

Use `Waymark: Show Code Context` to inspect what Waymark knows before using AI.

### Grounded AI assistance

Waymark can send an explicit selected-code request to a configured NVIDIA-hosted model.

AI requests are user-triggered only. Waymark does not run background AI, automatic AI, telemetry, analytics, embeddings, MCP, or repository-wide source scanning.

### Safe AI remediation proposals

For deterministic Guardian findings, Waymark can ask the configured NVIDIA model for a guarded remediation proposal.

Remediation is a proposal only. Waymark does not automatically edit files. Any edit requires explicit user approval and is checked against file-change safety conditions before it is applied.

## How it works

```text
Project rules and decisions
        ↓
Code changes or selected code
        ↓
Deterministic Guardian
        ↓
Evidence-backed findings
        ↓
Optional grounded AI assistance
```

Guardian is deterministic and local. AI assistance is optional and explicit.

## Getting started

1. Install Waymark.
2. Open a project in VS Code.
3. Run `Waymark: Initialize Project`.
4. Create or import project rules.
5. Add architectural decisions as they are made.
6. Start coding, or use an AI coding agent.
7. Waymark checks relevant changed files automatically and reports deterministic findings in VS Code diagnostics and the Waymark Activity Bar.

The selected-code right-click menu is the fastest workflow:

1. Select code in the editor.
2. Right-click.
3. Open the `Waymark` submenu.
4. Choose `Explain Selected Code`, `Ask About Selected Code`, `Check Against Project Rules`, or `Show Code Context`.

## Using Waymark with Codex or other coding agents

Waymark project truth is plain Markdown so coding agents can inspect it before changing code.

When using Codex or another coding agent, instruct it to:

1. Read `.trail/constitution.md`.
2. Read active `.trail/rules/*.md`.
3. Read relevant `.trail/decisions/*.md`.
4. Respect those constraints before changing code.
5. Never invent or add new project rules without explicit user approval.

Agent compliance is useful, but Guardian remains the deterministic local verification layer.

## NVIDIA AI setup

Waymark AI features are optional. Local Project Memory, Guardian, rules, decisions, Git context, and Code Context continue to work without NVIDIA configuration.

Waymark reads NVIDIA configuration from the VS Code extension host process environment:

- `NVIDIA_API_KEY`
- `NVIDIA_MODEL`

Windows PowerShell user-level example:

```powershell
[Environment]::SetEnvironmentVariable(
    "NVIDIA_API_KEY",
    "YOUR_KEY",
    "User"
)

[Environment]::SetEnvironmentVariable(
    "NVIDIA_MODEL",
    "meta/llama-3.1-8b-instruct",
    "User"
)
```

Optional overrides:

```powershell
[Environment]::SetEnvironmentVariable("NVIDIA_BASE_URL", "https://integrate.api.nvidia.com/v1", "User")
[Environment]::SetEnvironmentVariable("NVIDIA_TIMEOUT_MS", "30000", "User")
[Environment]::SetEnvironmentVariable("NVIDIA_MAX_RETRIES", "2", "User")
[Environment]::SetEnvironmentVariable("NVIDIA_MAX_TOKENS", "900", "User")
[Environment]::SetEnvironmentVariable("NVIDIA_TEMPERATURE", "0.2", "User")
```

Restart VS Code after changing user-level environment variables so the extension host process can see them.

Never paste an API key into source code. Never commit an API key to the repository. Waymark does not persist or display the API key value.

## Privacy and security

- Guardian checks are local and deterministic.
- Automatic Guardian does not call NVIDIA.
- Automatic Guardian does not upload source code.
- AI requests happen only after an explicit user action.
- AI requests send selected/contextual code and relevant project evidence to the configured NVIDIA endpoint.
- API keys are not persisted by Waymark.
- API keys are never displayed by Waymark.
- Waymark does not perform repository-wide source indexing.
- Waymark does not create an embeddings database.
- Waymark does not perform Git mutations.
- Waymark does not call a backend service.
- AI remediation never automatically edits files.
- Remediation requires explicit user approval before any edit is applied.

## Commands

### Project setup

- `Waymark: Initialize Project`
- `Waymark: Select Workspace Folder`
- `Waymark: Create Constitution`
- `Waymark: Open Constitution`

### Rules

- `Waymark: Add Rule`
- `Waymark: Delete Rule`
- `Waymark: Import Architecture Document`

### Decisions

- `Waymark: Add Decision`

### Guardian

- `Waymark: Check Against Project Rules`
- `Waymark: Check Current Changes`
- `Waymark: Show Guardian Finding`

### Project Memory and Git context

- `Waymark: Open Project Memory`
- `Waymark: Search Project Memory`
- `Waymark: Refresh Project Memory`
- `Waymark: Show Memory Result Details`
- `Waymark: Refresh Git Context`
- `Waymark: Open Changed File`
- `Waymark: View File Diff`
- `Waymark: Show Commit Details`

### Code Context and AI

- `Waymark: Show Code Context`
- `Waymark: Explain Selected Code`
- `Waymark: Ask About Selected Code`
- `Waymark: Show AI Configuration`

## Development

From the repository root:

```bash
corepack pnpm --filter ./apps/vscode-extension typecheck
corepack pnpm --filter ./apps/vscode-extension lint
corepack pnpm --filter ./apps/vscode-extension test
corepack pnpm --filter ./apps/vscode-extension build
corepack pnpm --filter ./apps/vscode-extension validate:manifest
```

### F5 Extension Development Host

1. Open this repository in VS Code.
2. Press F5 or choose `Waymark: VS Code Extension Development Host` in the Run and Debug panel.
3. VS Code runs the `Waymark: Build VS Code Extension` pre-launch task.
4. A separate Extension Development Host window opens with Waymark loaded from `apps/vscode-extension`.
5. In the development host, open a project and select the Waymark Activity Bar item.

### Local VSIX

Build a fresh local VSIX from the repository root:

```bash
corepack pnpm --filter ./apps/vscode-extension package:vsix
```

Install it through the VS Code UI:

1. Open VS Code.
2. Open Extensions.
3. Select `...`.
4. Select `Install from VSIX...`.
5. Choose the generated Waymark `.vsix`.
6. Reload VS Code if prompted.
7. Open a project.
8. Open the Waymark Activity Bar.

CLI alternative:

```bash
code --install-extension apps/vscode-extension/<generated-waymark-vsix-file>.vsix
```

F5 runs Waymark in a temporary Extension Development Host window. A VSIX installs Waymark into the normal VS Code environment.

## Limitations

- Guardian checks are deliberately conservative and pattern-based.
- Guardian reports potential conflicts; it does not prove code is correct.
- Automatic Guardian checks relevant changed files only; it does not perform full repository analysis.
- Project Memory is deterministic lexical retrieval, not semantic search.
- Waymark does not use embeddings or vector search.
- Waymark does not include MCP integration.
- AI features require user-triggered NVIDIA requests.
- AI responses may use general programming knowledge, but project-specific claims should be grounded in supplied Waymark evidence.
- The local project-memory directory is currently named `.trail/` for compatibility with the existing storage format.

## License

Waymark is licensed under the MIT License. See `LICENSE` for details.
