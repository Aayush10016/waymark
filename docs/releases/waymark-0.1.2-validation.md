# Waymark 0.1.2 validation

Audit date: 2026-10-22.

## Scope and results

The extension verification passes 223 automated tests in 29 files. These checks do not certify every possible natural-language rule or every editor interaction.

| Capability | Evidence |
| --- | --- |
| New project initialization | Real filesystem workflow creates `.waymark/`, not legacy storage |
| Legacy project detection and migration | Marker/source/memory tests cover legacy reading, canonical precedence, explicit confirmation boundary, conflict refusal, and preservation |
| Constitution, rules, decisions | Create/read/delete rules, duplicate-safe creation, decision numbering/status, existing-file preservation, and memory retrieval tests |
| Guardian rule syntax | Matrix covers supported prohibition phrases, imports, dependencies, PostgreSQL persistence constraints, authentication component signals, technology aliases, and all supported scopes |
| Guardian runtime | Adapter tests cover diagnostics, per-file stale-result protection, unrelated-file findings, and rechecks after canonical/legacy rule changes or deletion |
| Project Memory | Lexical retrieval, ranking, refresh, and workspace cache isolation tests |
| Selected Code Context | Bounded context and regression test for a changed selection |
| Git | Read-only client/parser tests; no mutating Git operations in extension runtime |
| AI grounding | Evidence selection, budgets, citation mapping, malformed responses, and secret-safe errors tested |
| AI remediation | Structured response parsing, user-approval boundary implementation, stale-file rejection, expected-text validation, unrelated-file rejection, integral ranges, and overlapping-edit rejection |
| NVIDIA live request | Approved MedicineVerifier.sol request succeeded using `nvidia/nemotron-3.5-lightning-30b-a3b` with 4096 output tokens in approximately 15 seconds |
| Package | Manifest, namespace, icons, compiled output, README, and forbidden-artifact inspection |

## Fixed during this audit

- Project Memory no longer reuses another workspace's cached index.
- Show Code Context rebuilds from the current selection.
- AI remediation rejects overlapping changes and non-integral line ranges.
- Changes or deletion of project-memory rules trigger rechecks of already tracked source files.
- Unavailable NVIDIA model/endpoint responses (404/410) are actionable and are not retried.
- Canonical project-memory files are excluded from the VSIX.

## Limits and remaining live checks

- A complete real-editor walkthrough of rule/decision dialogs, search navigation, approved remediation application, and restart behavior remains incomplete. An attempted separate extension-test host did not produce a result artifact. Unit/adapter/filesystem tests are not presented as that live walkthrough.
- The running VS Code window successfully loaded Waymark 0.1.2 and identified existing legacy project memory with its migration action.
- The live NVIDIA provider request succeeded; the complete running-editor AI flow after a full environment refresh remains a separate check.
- The live model answer incorrectly named Solidity's generated mapping getter. Model answers require review.
- Guardian is pattern-based. Arbitrary prose, unsupported scopes, unsupported languages, and arbitrary architectural invariants are not guaranteed to be checked.
- Check Current Changes uses Git path metadata. Automatic Guardian checks supported changed-file contents; selected-code checks inspect supplied code context.
- No claim of universal correctness, hallucination prevention, or exhaustive manual certification is made.

## Release checks

Extension lint, typecheck, tests, build, and manifest validation passed after the audit fixes. Root `corepack pnpm verify` also passed, including format, secret and fixture scans, all workspace tests/builds, and OpenAPI consistency. Inspect the rebuilt VSIX before publication. Public availability must be verified separately after uploading the package.
