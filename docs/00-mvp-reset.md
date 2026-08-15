# Pivot Decisions

## What changed

The previous product treated an ordered browsing trail as the durable object. That was too close to browser history and tab management. The new durable object is a **solved engineering problem**.

A sequence of pages has little lasting value without knowing:

- what the developer was trying to fix;
- which evidence mattered;
- what change actually solved it;
- whether the current problem resembles it.

Visited pages are now supporting evidence inside a Problem Session, never the product itself.

## The killer feature

When current debugging behavior resembles a previously solved Problem Session, Trail proactively surfaces the developer’s own final solution and supporting resources.

The magic is not semantic search. It is timely recognition:

> “You solved something very similar 183 days ago. Here is what fixed it.”

## Hard product truths

### Trail cannot reliably know that a problem is solved

Browsing stopped can mean solved, abandoned, interrupted, or lunch. Trail may ask once after a likely session boundary, but only the developer can confirm success and provide the final solution. No AI-generated text may be stored as the authoritative solution.

### Metadata cannot recover full AI conversations

URL and title capture cannot reconstruct the contents of ChatGPT, Claude, Gemini, or IDE conversations. MVP stores an AI conversation as a titled link plus an optional user note. Vendor-specific conversation import or content capture requires explicit integrations and is deferred.

### Similarity scores are not probabilities

“91% confidence” implies calibration the MVP will not possess. The product may show `Strong match`, `Possible match`, or a calibrated score only after evaluation proves the number corresponds to real relevance.

### “Time saved” cannot be known automatically

The difference between two session durations is not causal time saved. MVP does not display invented estimates. It may later show the duration of the previous session or ask users whether the recommendation helped.

### Automatic recognition has a cold-start problem

Trail cannot retrieve a personal solution before the user has recorded one. Onboarding must state this honestly and use a synthetic demo. Retention depends on making the first solved session effortless to save.

## What remains from the previous product

- Quiet, privacy-conscious browser metadata capture.
- Ordered evidence within a session.
- Replay of selected resources.
- Conservative, invisible AI.
- Chrome-first distribution.

## What is removed

- “Save recent browsing” as the primary value.
- General browser-history search.
- Generic research trails unrelated to a solved problem.
- Bookmark and tab-session positioning.
- Local-only as an absolute architecture constraint; cross-session embeddings require a small backend in the MVP.
- Split/merge editors, branches, statuses beyond candidate/solved/dismissed, folders, notes systems, collaboration, and dashboards.
- Chat, answer generation, agentic debugging, code execution, repository indexing, IDE plugins, and full-page capture.

## MVP product boundary

MVP supports one loop:

1. infer a likely debugging session from minimal browser metadata;
2. let the user confirm or dismiss it;
3. ask “What finally solved it?” once;
4. store a compact solved Problem Session;
5. compare a future likely problem against solved sessions;
6. show one high-confidence memory card;
7. let the user replay the final solution and resources.

The user can also manually start or finish a Problem Session. This is a necessary escape hatch, not a separate product mode.

## MVP architecture decisions

- One Chrome extension containing popup and full-page UI.
- One backend application.
- One PostgreSQL database with pgvector.
- One model provider behind a narrow internal adapter.
- HTTPS polling/request-response only.
- No Redis, WebSockets, graph database, object storage, message broker, microservices, or separate web application.
- AI enrichment occurs at candidate closure and similarity checks, not on every page.

## Success test

Trail succeeds only if retained users receive relevant prior-solution recommendations and act on them. Captured events, created sessions, model calls, and search volume are not success metrics.
