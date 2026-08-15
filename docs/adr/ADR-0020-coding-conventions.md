# ADR-0020: Automated Java and TypeScript Coding Conventions

- **Status:** Accepted
- **Date:** 2026-07-05
- **Decision owners:** Technical Lead, Frontend Owner

## Context

Five engineers need consistent code review, security, naming, and boundary behavior without subjective style debate.

## Decision

Adopt the frozen conventions in Engineering Blueprint §10. Automate formatting, linting, import order, strict TypeScript, Java static analysis, architecture checks, and changed-line coverage in CI. Use constructor injection, framework-free domain code, immutable DTOs, no Lombok, no unsafe TypeScript escapes, and no direct Chrome/API/storage calls from UI components.

## Alternatives considered

- Team-style-by-review only: inconsistent and expensive.
- Google style without Trail rules: useful baseline, misses product/security boundaries.
- Lombok/code-generation-heavy conventions: less boilerplate, hidden semantics/tool coupling.
- Maximum static rules immediately: noisy and adoption-hostile.

## Why selected

Automation preserves review attention for correctness, privacy, architecture, and UX.

## Trade-offs

- Tool setup and occasional false positives.
- Some verbosity without Lombok.
- Rules need deliberate evolution.

## Risks

- Coverage/lint metrics are gamed.
- Suppressions accumulate.
- Formatting tools create noisy migration diffs.

## One-million-user review

No bottleneck; conventions become more valuable with team size. Excessive CI time/rules can slow delivery.

## Future migration path

Version shared rules, measure suppression/CI pain, and change via ADR only when broad/noisy. Apply mechanical formatting migrations separately from behavior changes.

## Consequences

PRs failing automated standards do not merge. Exceptions are local, explained, and time-bounded where possible.
