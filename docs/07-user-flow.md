# User Flow

## 1. Install and consent

1. User installs Trail.
2. Onboarding explains the product with a synthetic solved-problem recall.
3. Disclosure lists captured metadata, excluded content, AI use, retention, and deletion.
4. User signs in, grants required Chrome permissions, optionally blocks domains, and enables capture.

No data is collected before consent.

## 2. Debug normally

1. User searches an error or technical question.
2. Trail records sanitized top-level metadata and search queries.
3. Deterministic signals observe reformulated queries, technical domains, repository/docs transitions, and error-like text.
4. A high-confidence sequence becomes a hidden candidate Problem Session.
5. The popup may show a quiet `Problem session active` state; it does not interrupt or offer advice.

The user may click `Start problem` when Trail misses the beginning.

## 3. End-state prompt

After inactivity or explicit `Finish problem`:

1. Trail shows the inferred problem title and a compact evidence preview.
2. It asks `Did you solve it?`
3. If yes, the only required prompt is `What finally solved it?`
4. User writes one or two sentences.
5. Optional actions: edit problem title, add a short description/note, remove irrelevant resources.
6. User saves the solved Problem Session.

If `Not solved`, retain only if the user explicitly chooses to keep an unresolved candidate. If `Remind me later`, prompt at most once more. Dismissed candidates are deleted after a short retention window.

## 4. Enrichment

Trail asynchronously:

- summarizes the problem, never the final solution as if authoritative;
- extracts technologies, framework names, problem type, and error phrases;
- creates one session embedding;
- indexes the session for personal search and similarity.

The session is usable immediately from deterministic fields if AI is delayed.

## 5. Similar problem begins

1. A future browsing sequence again crosses a debugging threshold.
2. Trail creates a bounded current-problem representation.
3. The backend retrieves only the user’s solved sessions.
4. Vector similarity is combined with exact error, technology, and lexical overlap.
5. If the conservative threshold is met, Trail shows one card.

No match produces no UI.

## 6. Similar Problem Found card

The card contains:

- `Similar problem found`;
- `Strong match` or `Possible match`;
- prior problem title and age;
- the developer’s exact previous solution;
- technologies or error terms explaining the match;
- representative resources and previous session duration;
- `Replay solution`, `Helpful`, `Not related`, and dismiss.

It does not contain a chat box, generated diagnosis, fabricated time savings, or an uncalibrated percentage.

## 7. Replay

`Replay solution` opens the Problem Session. The final solution is visually primary; evidence is ordered underneath. The user can open individual or selected resources. Bulk opening requires confirmation.

## 8. Manual memory search

The extension page contains one search field over solved problems. Results show problem title, final solution excerpt, technologies, date, and representative resources. Raw browsing events are never exposed as a general history product.

## 9. Privacy controls

The popup provides pause/resume and `Block this domain`. Settings provide exclusions, account deletion, and session deletion. Deletion removes active source records, summaries, search documents, and embeddings.
