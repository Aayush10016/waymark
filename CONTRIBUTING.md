# Contributing to Trail

Trail is currently in architecture freeze preparation. Contributions should resolve approved M0 decisions or documentation issues; product implementation requires the Ready for Development gates.

## Workflow

1. Start from a scoped GitHub issue with acceptance criteria, dependencies, estimate, labels, and verification plan.
2. Use a short branch: `feat/123-description`, `fix/123-description`, or `chore/123-description`.
3. Keep one outcome per pull request and link the issue.
4. Follow the pull request template, including security/privacy, compatibility, tests, and rollback.
5. Obtain required review and squash merge into protected `main`.

## Standards

The authoritative standards are in [Engineering Blueprint §10](docs/18-engineering-blueprint.md#10-development-standards).

- Package by capability and respect inward dependency rules.
- Preserve the developer-authored final-solution invariant.
- Never put URLs, queries, solutions, tokens, prompts, model output, or user data in logs, issues, screenshots, or fixtures.
- Use synthetic data in tests and documentation.
- Update OpenAPI, migrations, ADRs, threat model, runbooks, and UX state evidence with the behavior they govern.
- Do not introduce Redis, WebSockets, content scripts, microservices, new AI behavior, or infrastructure without the documented trigger and ADR.

## Commits and pull requests

PR titles follow Conventional Commit style. Individual commits should be comprehensible; the squash commit becomes the mainline history.

Prefer fewer than 400 changed hand-written lines. Larger changes require a review guide and should be split when they contain independent outcomes.

## Definition of Done

Acceptance criteria, automated tests, security/privacy review, compatibility, accessibility/UX states, documentation, content-safe diagnostics, and rollback/disable path are complete. CI is green and required reviewers approve.
