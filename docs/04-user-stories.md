# User Stories

Priority `P0` means the killer loop cannot work without it. `P1` is evidence-gated after the private beta.

## P0 — Trust and capture

### US-1 Understand capture

As a developer, I understand what Trail records, what it never records, and how AI uses my metadata before capture begins.

### US-2 Pause and exclude

As a developer, I pause capture or block a domain before its metadata leaves my browser.

### US-3 Preserve debugging context

As a developer, Trail quietly records eligible searches and technical resources while I debug without asking me to save every page.

## P0 — Problem Session

### US-4 Detect a likely problem

As a developer, I receive a conservative candidate Problem Session when my browsing strongly resembles debugging.

### US-5 Start or finish manually

As a developer, I can explicitly start or finish a Problem Session when automatic boundaries are wrong.

### US-6 Review the problem

As a developer, I rename the problem, add a short description or note, remove irrelevant resources, dismiss it, or delete it.

### US-7 Record the final solution

As a developer, I answer `What finally solved it?` in one or two sentences and Trail preserves my exact wording.

Acceptance: `Not solved` and `Remind me later` are equally valid; AI cannot mark the session solved.

## P0 — Recall

### US-8 Receive a similar-problem memory

As a developer beginning a related debugging session, I see one prior solved problem only when Trail has strong evidence of relevance.

### US-9 Understand the match

As a developer, I see the prior problem, age, technologies/error overlap, my final solution, and representative resources without opening a chatbot.

### US-10 Replay the solution

As a developer, I open the full prior Problem Session or selected resources and mark the recommendation helpful or unrelated.

### US-11 Search memories

As a developer, I manually search solved Problem Sessions when proactive recall does not trigger.

## P0 — Data control

### US-12 Delete

As a developer, I delete a Problem Session or my account, including derived summaries and embeddings.

## P1

- Better deterministic session boundaries from correction data.
- Optional import of a specific AI conversation through an approved integration.
- Cross-device capture and sync.
- One-line annotations on individual resources.
- IDE or terminal context spike.

## Rejected stories

- Chat with engineering history.
- Ask Trail to solve the current problem.
- Generate a final solution from visited pages.
- Index entire repositories or terminals.
- Build folders, canvases, wikis, graph views, or dashboards.
- Share passive personal sessions with employers or teammates.
