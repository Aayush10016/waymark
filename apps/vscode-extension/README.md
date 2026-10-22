# Waymark

Keep AI-assisted development aligned with your project's rules, decisions, and engineering constraints.

AI coding agents can make large changes quickly. They do not automatically know every project-specific engineering rule, architecture decision, dependency constraint, or security requirement. Waymark keeps those constraints inside the project and independently checks relevant changed code using a deterministic Guardian.

Core idea:

```text
AI agents make changes.
Waymark checks whether those changes follow the project's explicit rules.
```

Waymark helps detect project-rule violations introduced by AI-assisted code changes.

## Why Waymark?

AI-assisted development creates a specific review problem:

```text
AI agent
-> changes many files
-> developer must manually inspect whether project-specific rules were violated
```

Waymark adds an independent verification layer:

```text
Project rules
-> AI-assisted changes
-> relevant changed files detected
-> deterministic Guardian
-> evidence-backed findings
```

The result is a local, project-specific guardrail around AI-assisted development. Waymark reports potential conflicts against the rules and decisions you explicitly store in the repository.

## Quick Start

1. Install Waymark from the VS Code Marketplace.
2. Open a project in VS Code.
3. Run `Waymark: Initialize Project`.
4. Add project rules using `Waymark: Add Rule`.
5. Add architectural decisions using `Waymark: Add Decision`.
6. Start coding or use Codex, Copilot, or another AI coding agent.
7. Waymark automatically checks relevant changed files.
8. Review Guardian diagnostics when a rule conflict is found.
9. Optionally configure NVIDIA AI for explicit AI assistance.

The core Waymark Guardian works without NVIDIA AI.

## Automatic Guardian

Automatic Guardian is the core Waymark workflow.

It:

- watches relevant changed files;
- detects ordinary edits;
- detects AI-agent edits;
- checks active project rules;
- runs locally;
- is deterministic;
- does not require NVIDIA;
- does not upload source code;
- does not perform repository-wide source indexing;
- reports findings through VS Code diagnostics;
- updates findings when files change;
- does not automatically modify files;
- does not automatically revert files.

Example project rule:

```text
Redis must not be used in the backend.
```

An AI coding agent adds:

```java
import org.springframework.data.redis.core.RedisTemplate;
```

Waymark detects the changed file and reports the rule conflict. The developer does not have to manually inspect every AI-modified file just to discover this kind of project-rule violation.

Guardian detects and reports. It does not automatically remove the violating code.

## Project Rules

Project rules are explicit constraints for the repository. Use them for:

- forbidden dependencies;
- architecture boundaries;
- security requirements;
- coding conventions;
- technology constraints.

Rules are stored as Markdown under:

```text
.waymark/rules/
```

### What Guardian can check

Guardian recognizes specific deterministic patterns, including `Do not use Redis`, `Do not import lodash`, `Do not use dependency axios`, `Use PostgreSQL for persistent storage`, and `Authentication must go through AuthService`. PostgreSQL persistence checks flag known conflicting database references; the architectural-component pattern currently checks direct authentication implementation signals.

Supported scopes are project-wide, backend, frontend, extension, vscode, java, typescript, javascript, and python. Backend/frontend scopes depend on matching workspace or directory names. Deprecated rules are ignored. Accepted decisions provide supporting evidence; decisions are not independently executable rules.

Arbitrary prose rules are stored as project evidence but are not automatically enforceable. A clean result means no supported pattern matched, not that every project requirement was verified. The explicit Check Current Changes command examines Git path metadata; Automatic Guardian checks the contents of supported files when they change. Use Check Against Project Rules on selected code for a content check of an existing selection.

## Project Decisions

Project decisions capture architectural choices and their rationale. They are stored as ADR-style Markdown files under:

```text
.waymark/decisions/
```

## Project Memory

Project Memory is the local evidence base Waymark uses for search, context, and Guardian checks.

It is built from:

- `.waymark/project.json`
- `.waymark/constitution.md`
- `.waymark/rules/*.md`
- `.waymark/decisions/*.md`
- bounded read-only Git metadata

Project Memory is:

- local;
- rebuildable;
- deterministic;
- lexical.

It does not use embeddings, vector search, or repository-wide source indexing.

## Project Memory Directory

Waymark stores project rules, architectural decisions, constitution data, and project memory in:

```text
.waymark/
├── project.json
├── constitution.md
├── rules/
└── decisions/
```

### Legacy Projects

Older Waymark projects may use `.trail/`, the previous on-disk project-memory format.

Waymark continues to support existing `.trail/` projects and can migrate them to `.waymark/` with explicit user approval.

## Works Without AI

You can use Waymark as a deterministic local engineering-governance tool without configuring NVIDIA AI.

These features do not require NVIDIA:

- Project Rules
- Project Decisions
- Project Memory
- Automatic Guardian
- Code Context
- Git context
- VS Code diagnostics

## Optional NVIDIA AI

NVIDIA AI is optional. The core Guardian does not require NVIDIA.

Users who want AI features must provide their own NVIDIA API key. Waymark does not provide a shared API key. You use a key associated with your own NVIDIA account.

AI features include:

- `Waymark: Explain Selected Code`
- `Waymark: Ask About Selected Code`
- AI remediation proposals

### Create an NVIDIA API Key

Official NVIDIA resources:

- NVIDIA Build: <https://build.nvidia.com/>
- NVIDIA API key management: <https://build.nvidia.com/settings/api-keys>
- NVIDIA NIM LLM API reference: <https://docs.api.nvidia.com/nim/reference/llm-apis>

Setup:

1. Open NVIDIA Build.
2. Sign in or create an NVIDIA account.
3. Open NVIDIA's API key management page.
4. Generate an API key using NVIDIA's current interface.
5. Copy the API key securely.
6. Keep the key private.
7. Never commit the key to Git.
8. Never place the key directly in source code.

### Configure NVIDIA on Windows

Use PowerShell:

```powershell
[Environment]::SetEnvironmentVariable(
    "NVIDIA_API_KEY",
    "YOUR_NVIDIA_API_KEY",
    "User"
)

[Environment]::SetEnvironmentVariable(
    "NVIDIA_MODEL",
    "nvidia/nemotron-3.5-lightning-30b-a3b",
    "User"
)
```

Replace `YOUR_NVIDIA_API_KEY` with the real key. Never commit it. Never put it in source code. Never expose it in screenshots or logs.

Optional settings:

```powershell
[Environment]::SetEnvironmentVariable(
    "NVIDIA_BASE_URL",
    "https://integrate.api.nvidia.com/v1",
    "User"
)

[Environment]::SetEnvironmentVariable(
    "NVIDIA_TIMEOUT_MS",
    "30000",
    "User"
)

[Environment]::SetEnvironmentVariable(
    "NVIDIA_MAX_RETRIES",
    "2",
    "User"
)

[Environment]::SetEnvironmentVariable(
    "NVIDIA_MAX_TOKENS",
    "4096",
    "User"
)

[Environment]::SetEnvironmentVariable(
    "NVIDIA_TEMPERATURE",
    "0.2",
    "User"
)
```

Model availability changes. Select a currently available model in NVIDIA Build. The model above was tested with a 4096-token output budget; reasoning models may truncate answers with the default 900-token budget, so set `NVIDIA_MAX_TOKENS` to `4096` for this example. An HTTP 404 or 410 means the model or endpoint is unavailable; verify both `NVIDIA_MODEL` and `NVIDIA_BASE_URL`.

### Restart VS Code

After setting user-level environment variables:

1. Completely close VS Code.
2. Reopen VS Code.
3. Reopen the project.

The VS Code extension host must start with the updated environment variables.

### Verify NVIDIA Configuration

1. Open the Command Palette.
2. Press `Ctrl + Shift + P`.
3. Run `Waymark: Show AI Configuration`.

You should see configuration information such as:

```text
Provider: NVIDIA
API key: Configured
Model: configured
```

The actual API key is never displayed. If configuration is missing, check `NVIDIA_API_KEY`, `NVIDIA_MODEL`, and whether VS Code was restarted after setting them.

### Try an AI Feature

1. Open a source file.
2. Select code.
3. Right-click.
4. Open the Waymark submenu.
5. Choose `Waymark: Explain Selected Code`.

You can also run `Waymark: Ask About Selected Code`.

AI requests are explicitly user-triggered. Waymark does not make background NVIDIA requests. Automatic Guardian does not call NVIDIA.

## AI Remediation

Waymark uses a safe remediation workflow:

```text
Guardian finding
-> user requests AI fix suggestion
-> NVIDIA proposes a structured remediation
-> Waymark presents the proposal
-> user explicitly approves
-> Waymark applies a targeted edit
-> Guardian checks the affected file again
```

Waymark does not automatically modify files merely because an AI model suggested a fix.

## Privacy

| Feature | AI required | Sends code to NVIDIA |
| --- | --- | --- |
| Project Rules | No | No |
| Project Decisions | No | No |
| Project Memory | No | No |
| Automatic Guardian | No | No |
| Code Context | No | No |
| Explain Selected Code | Yes | Selected/contextual code |
| Ask About Selected Code | Yes | Selected/contextual code |
| AI remediation proposal | Yes | Required remediation context |

Guardian processing is local. When a user explicitly invokes an AI feature, selected/contextual code and relevant project evidence may be sent to the configured NVIDIA endpoint.

## Using Waymark with Codex, Copilot, and Other AI Agents

Project truth is stored in plain Markdown so AI coding agents can inspect it.

Tell your agent to read:

- `.waymark/constitution.md`
- `.waymark/rules/`
- `.waymark/decisions/`

Copyable instruction:

```text
Before modifying this project:

1. Read .waymark/constitution.md.
2. Read all active rules under .waymark/rules/.
3. Read relevant decisions under .waymark/decisions/.
4. Respect those constraints before changing code.
5. Do not introduce technologies, dependencies, or architectural patterns that violate active rules.
6. Do not create or modify project rules unless explicitly instructed by the user.
```

Even if an AI agent misses a constraint, Waymark's deterministic Guardian independently checks the resulting changed code.

## Code Context

Waymark can prepare bounded context for selected code.

Context may include:

- selected code;
- bounded surrounding code;
- file metadata;
- relevant Project Memory evidence;
- bounded Git evidence.

This context can be inspected before an explicit AI request.

## Privacy and Security

- Guardian checks are local and deterministic.
- Automatic Guardian does not call NVIDIA.
- Automatic Guardian does not upload source code.
- AI requests happen only after explicit user action.
- Explicit AI requests may send selected/contextual code to NVIDIA.
- API keys are not persisted by Waymark.
- API keys are never displayed by Waymark.
- Waymark does not add telemetry.
- Waymark does not add analytics.
- Waymark does not perform repository-wide source indexing.
- Waymark does not create an embeddings database.
- Waymark does not use vector search.
- Waymark does not use MCP.
- Waymark does not perform Git mutations.
- Guardian does not require a backend service.
- AI remediation requires explicit user approval.

## Commands

Project setup:

- `Waymark: Initialize Project`
- `Waymark: Select Workspace Folder`
- `Waymark: Create Constitution`
- `Waymark: Open Constitution`
- `Waymark: Migrate Legacy Project Memory`

Rules:

- `Waymark: Add Rule`
- `Waymark: Delete Rule`
- `Waymark: Import Architecture Document`

Decisions:

- `Waymark: Add Decision`

Guardian:

- `Waymark: Check Against Project Rules`
- `Waymark: Check Current Changes`
- `Waymark: Show Guardian Finding`

Project Memory and Git:

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

From the repository root:

```powershell
corepack pnpm --filter ./apps/vscode-extension typecheck
corepack pnpm --filter ./apps/vscode-extension lint
corepack pnpm --filter ./apps/vscode-extension test
corepack pnpm --filter ./apps/vscode-extension build
corepack pnpm --filter ./apps/vscode-extension validate:manifest
```

To launch an Extension Development Host, open the repository in VS Code and press `F5`.

To package locally:

```powershell
corepack pnpm --filter ./apps/vscode-extension package:vsix
```

To install the local VSIX manually:

```powershell
code --install-extension apps/vscode-extension/waymark-0.1.2.vsix --force
```

## Limitations

- Automatic Guardian currently watches Java, TypeScript/TSX, JavaScript/JSX, Python, C#, C/C++ headers and source, XML, YAML, JSON, and properties files. Other file types, including Solidity, are not automatically checked.
- Guardian checks are deliberately conservative and pattern-based.
- Guardian reports potential conflicts; it does not prove code correctness.
- Automatic Guardian checks relevant changed files rather than performing full repository analysis.
- Project Memory uses deterministic lexical retrieval.
- No embeddings or vector search.
- No MCP.
- AI responses depend on the configured NVIDIA model.
- AI requires the user's own NVIDIA configuration.
- Existing `.trail/` projects are supported for legacy compatibility.
- Migration to `.waymark/` requires explicit user approval.

## License

Waymark is licensed under the MIT License. See the repository `LICENSE` file.
