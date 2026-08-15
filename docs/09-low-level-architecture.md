# Low-Level Architecture

## Repository shape

```text
trail/
  backend/
    identity/
    capture/
    problems/
    intelligence/
    retrieval/
    privacy/
  extension/
    background/
    popup/
    app/
    capture/
    storage/
  contracts/
  docs/
```

This is one backend deployment and one extension artifact. Folders express ownership, not microservices.

## Backend responsibilities

### Identity

Owns normalized email identity, Argon2id password hashes, email verification, single-use password reset tokens, short-lived asymmetric JWT access tokens, and hashed opaque refresh sessions with rotation/reuse detection. Social login is deferred.

### Capture

Validates sanitized event batches, enforces consent and ownership, deduplicates client event IDs, applies retention, and exposes recent candidate state.

### Problems

Owns Problem Session lifecycle:

```text
CANDIDATE -> REVIEWABLE -> SOLVED
     |            |          |
     +----------> DISMISSED  +-> DELETED
     +----------> UNSOLVED
```

`SOLVED` requires non-empty user-authored final-solution text. AI jobs cannot perform this transition.

### Intelligence

Owns deterministic session signals, structured AI enrichment, embeddings, model versions, input fingerprints, retry state, and provider budgets.

### Retrieval

Owns solved-session search, current-to-prior similarity, thresholding, match evidence, suppression, and feedback.

### Privacy

Owns consent evidence, retention, account/session deletion, and content-safe audit events.

## Debugging detection

MVP uses an interpretable weighted score. Positive signals include:

- search queries containing error phrases or technical intent;
- query reformulation around a stable token set;
- transitions among search, official docs, GitHub, Stack Overflow, Reddit, YouTube, and AI-chat hosts;
- repeated framework/library vocabulary in titles;
- multiple technical sources within a bounded time window;
- manual `Start problem`.

Negative signals include isolated visits, feeds, entertainment, shopping, routine SaaS, long topic gaps, and manual dismissal constraints.

The detector produces reason codes and a configuration version. It does not call an LLM per page.

## Session boundary behavior

- Keep only a small set of recent candidate windows per user.
- Prefer time gaps and topic shifts over complex clustering.
- Manual start/finish is authoritative.
- Do not implement split/merge in MVP; an incorrect candidate can be dismissed and manually restarted.
- Prompt only after inactivity or explicit finish, never while the developer is actively navigating.

## Similarity ranking

Candidate generation uses:

- session embedding similarity;
- exact/normalized error phrase overlap;
- technology/framework intersection;
- weighted lexical search across problem, solution, and artifacts.

The final ranker requires both semantic similarity and at least one supporting signal unless the vector threshold is proven exceptionally precise. Technology overlap alone is insufficient: two unrelated Spring problems are not the same problem.

Do not use a generative reranker. Match explanations are deterministic, such as `Same CORS error phrase` or `Matched Spring Security + JWT filter`.

## Recommendation suppression

- Maximum one proactive card per current candidate.
- Never repeat the same prior-session recommendation during that candidate after dismissal.
- `Not related` creates a user-specific negative pair used for evaluation and future thresholding.
- Do not automatically learn a new model online from one feedback event.

## PostgreSQL-backed jobs

Use a small jobs table for enrichment and deletion work. A scheduled task claims ready rows with database locking, retries transient failures with backoff, and marks permanent failures. This avoids Redis while preserving durable retries.

## Failure behavior

- AI unavailable: save session immediately; show enrichment pending only in detail view; use lexical retrieval.
- Embedding unavailable: do not show a weak proactive match solely to fill the UI.
- Capture offline: extension queues bounded sanitized events and retries.
- Candidate detector wrong: user dismisses or manually starts/finishes.
- Model extraction wrong: user can correct visible technologies/problem type after MVP; final solution remains untouched.

## Testing priorities

- URL/query sanitation and prohibited-host corpus.
- Capture deduplication and offline retry.
- Candidate prompt rate and manual-boundary precedence.
- Invariant that only users can create/modify authoritative final solutions.
- Cross-user authorization for every query.
- Similarity evaluation against positive and hard-negative problem pairs.
- Recommendation suppression and feedback.
- Provider outage and retry behavior.
- Deletion of source and derived records.

Avoid testing speculative split/merge, collaboration, graph traversal, distributed caching, or multi-region behavior.
