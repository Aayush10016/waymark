# Product Requirements Document

## Product summary

Trail is an AI-powered engineering memory for developers. It records the context of a debugging session, captures the developer-verified final solution, and proactively recalls that solution when a similar problem occurs later.

## Product promise

**Never solve the same problem twice.**

## Problem

Developers repeatedly encounter similar configuration errors, dependency conflicts, build failures, integration bugs, and framework behavior. Existing tools preserve fragments:

- browsers preserve pages;
- bookmarks preserve chosen links;
- issue trackers preserve team work items;
- AI products preserve conversations inside one vendor;
- notes preserve only what the developer remembered to write.

None reliably connects the original problem, debugging evidence, and verified final solution, then recalls it at the moment of recurrence.

## Core hypothesis

If Trail captures a solved problem with less than 30 seconds of user effort and later recalls the developer’s own solution during a similar debugging session, that recovery will be valuable enough for developers to keep capture enabled.

The highest-risk assumptions are:

1. metadata is sufficient to recognize useful problem similarity;
2. developers will consistently provide a short final solution;
3. proactive recommendations will be accurate enough not to become noise;
4. developers will trust the capture model.

## Primary entity: Problem Session

A Problem Session contains:

- user-authored or confirmed problem title and description;
- ordered searches and visited technical resources;
- GitHub repositories/issues, documentation, videos, discussions, and AI-chat links;
- optional short local note;
- developer-authored final solution;
- extracted technologies, frameworks, problem type, error phrases, and tags;
- start/end time, duration, and creation date;
- one session-level embedding and its model version.

Raw browsing history outside a candidate or confirmed Problem Session is short-lived input, not a permanent product object.

## MVP functional requirements

### FR-1 Trust and onboarding

- Explain exactly which metadata is captured and why.
- Capture remains off until explicit consent.
- Exclude Incognito, local/private hosts, prohibited schemes, page bodies, keystrokes, forms, cookies, and credentials.
- Provide pause, block-domain, delete-session, and delete-account controls.
- Show a synthetic similar-problem demo without claiming the user already has memories.
- Support email/password registration, verification, sign-in, refresh, logout, and password reset; no social login in v1.1.

### FR-2 Debugging-session detection

- Observe sanitized top-level URL, title, search query from allowlisted search engines, time, tab lineage, and coarse resource type.
- Use deterministic signals first: error-like queries, query reformulation, technical domains, repository/documentation transitions, and repeated related navigation.
- Create only conservative candidate sessions.
- Provide manual `Start problem` and `Finish problem` controls.
- Never classify ordinary browsing as a solved problem without user confirmation.

### FR-3 Session review

- Show the inferred problem title and ordered evidence.
- Allow rename, short problem description, removal of irrelevant resources, one optional note, dismiss, and delete.
- Do not require split, merge, branches, folders, or detailed editing in MVP.

### FR-4 Solution capture

- After a likely session boundary, ask once: `What finally solved it?`
- Allow `Solved`, `Not solved`, and `Remind me later` outcomes.
- Require a developer-authored final solution before marking a session solved.
- Preserve the user’s wording exactly; generated summaries are secondary and labeled.

### FR-5 Invisible AI enrichment

- Generate a concise problem summary from sanitized metadata and user text.
- Extract technologies/frameworks and a broad problem category.
- Extract normalized error phrases when present.
- Create one embedding for the solved session.
- Do not generate the final solution, diagnose the current issue, chat, browse, execute code, or recommend web answers.
- Route structured enrichment and embeddings through independent provider abstractions selected by configuration.
- Persist provider/model/prompt/embedding provenance and structured evidence without unrestricted raw provider payloads.

### FR-6 Similar-problem recognition

- Build a bounded representation of a current likely debugging session.
- Compare it only against the authenticated user’s solved sessions.
- Require a conservative threshold plus supporting lexical/technology evidence.
- Show at most one proactive card per current session in MVP.
- Use `Strong match` or `Possible match`; do not expose an uncalibrated percentage.
- Allow `Helpful`, `Not related`, and dismiss feedback.
- Provide deterministic `Why this?` evidence referencing prior sessions, errors, technologies, queries, or artifacts used by retrieval.

### FR-7 Replay

- The recommendation card shows previous problem, age, final solution, key technologies, prior session duration, and representative resources.
- `Replay solution` opens the prior Problem Session.
- The user may open individual or selected resources with a safety confirmation for bulk opening.

### FR-8 Manual retrieval

- Provide simple search across problem title, description, final solution, technologies, error phrases, and resource titles/domains.
- Return solved Problem Sessions, never raw browser history or generated answers.

## Non-goals

- AI chatbot, coding assistant, answer engine, autonomous debugger, or code generator.
- IDE integration, repository indexing, terminal capture, clipboard monitoring, or code execution.
- Full AI conversation ingestion.
- Note-taking workspace, wiki, graph visualization, tags hierarchy, or bookmarks.
- Team sharing, public profiles, analytics dashboards, or employer monitoring.
- Mobile, Firefox, Safari, native desktop, or multiple Chrome profiles.

## Quality requirements

- A candidate must appear within two minutes of a qualifying session boundary when online.
- Solution capture must take under 30 seconds at median.
- No more than one unsolicited prompt per active debugging session.
- No more than one proactive similarity card per session in MVP.
- Exact deletion removes active records and derived embeddings from normal reads immediately.
- Every user-data query is scoped by authenticated user at the database boundary.

## Success metrics

### North star

**Helpful prior-solution recalls per retained active user.**

A recall counts when a user marks a proactive match helpful or opens its solution/resources, excluding same-day self-tests.

### Activation

- User records one solved Problem Session within seven active days.
- Final solution is completed in under 30 seconds.

### Quality

- At least 70% of shown proactive matches are marked helpful or opened during private beta.
- Fewer than 0.25 irrelevant proactive cards per active debugging day.
- At least 80% of solved-session technology extractions require no correction in sampled review.

### Retention evidence

- Share of activated users recording a second solved session.
- Share receiving a useful recall older than seven days.
- Capture pause/uninstall rate and stated privacy reason.

Do not optimize event volume, session count, model calls, or time spent in Trail.

## MVP acceptance scenario

1. A developer researches `Spring Security CORS not working` across search, documentation, Stack Overflow, GitHub, and an AI-chat link.
2. Trail forms one candidate session and asks after the session becomes inactive.
3. The developer confirms the problem and writes: `Allowed localhost:3000 in CorsConfiguration and placed the JWT filter before UsernamePasswordAuthenticationFilter.`
4. Trail stores the solved Problem Session and enriches it with `Spring Security`, `CORS`, `JWT`, and `configuration`.
5. During a later session with related error/search signals, Trail shows one strong-match card containing that exact user-authored solution.
6. The developer opens the prior session or a supporting resource and marks the match helpful.

## Release blockers

- The extension requires permissions that cannot be truthfully justified.
- Final solutions can be overwritten or hallucinated by AI.
- Similarity queries can return another user’s data.
- Raw URLs/titles/searches leak into logs or model payloads outside policy.
- Candidate or recommendation noise exceeds the beta threshold.
- Account/session deletion does not remove derived embeddings from active systems.
