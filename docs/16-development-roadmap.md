# Development Roadmap

## Constraints

- One engineer.
- Four months to a credible MVP decision.
- Phase 1 must fit within four weeks.
- One Chrome extension, one backend application, one PostgreSQL/pgvector database.
- No chatbot, code assistant, IDE plugin, collaboration, Redis, WebSockets, or microservices.
- Java 17, Spring Boot 3.5.15, Maven 3.9.16, PostgreSQL 17, and pgvector 0.8.2.
- Email/password authentication only; social/OIDC login is deferred.
- Render Docker deployment with Actuator/Micrometer/structured logs and typed global feature flags.

## Phase 1 — End-to-end killer loop (four weeks)

Goal: prove the complete interaction with a small dogfood build, including a deliberately seeded or previously recorded solved Problem Session.

### Week 1 — De-risk capture, privacy, and retrieval

Deliver:

- Chrome permission and MV3 lifecycle spike.
- Sanitized top-level URL/title/search-query capture.
- Local exclusion, pause, and prohibited-host tests.
- Minimal backend, email/password authentication foundation, PostgreSQL schema, and capture batch.
- Minimal health/readiness/liveness, private metrics, structured JSON logs, and global feature-flag service.
- pgvector spike using a hand-labeled set of similar and hard-negative engineering problems.
- Synthetic similar-problem demo card.

Exit:

- No prohibited metadata reaches the server in packet inspection.
- Extension survives worker/browser restart with no duplicate loss.
- Embedding baseline separates at least obvious positive/negative examples well enough to continue; threshold is not declared production-ready.

Cut if late: visual styling and broad resource classification. Do not cut permission/privacy validation.

### Week 2 — Create a solved Problem Session

Deliver:

- Deterministic debugging signals and one candidate window.
- Manual `Start problem` and `Finish problem`.
- Candidate review: title, ordered resources, remove, dismiss.
- `Did you solve it?` and `What finally solved it?` flow.
- Exact user-authored final-solution persistence.
- Problem Session detail and selected-resource replay.

Exit:

- Five real debugging sessions can be captured, reviewed, solved, and replayed.
- Median final-solution flow is under 30 seconds.
- AI cannot create or overwrite solved status/final solution.

Cut if late: problem description and optional note. Do not cut manual boundaries or solution authorship.

### Week 3 — Enrich and retrieve memory

Deliver:

- Structured summary, technology/framework, problem-type, and error-phrase extraction.
- One solved-session embedding and PostgreSQL-backed retry job.
- Solved-problem library and simple lexical/semantic search.
- Current-candidate representation and user-scoped similarity query.
- Deterministic match evidence.

Exit:

- Known positive problem pairs retrieve at rank one in the small evaluation set.
- Hard negatives sharing only a framework do not trigger.
- AI outage does not block solution save or lexical retrieval.

Cut if late: generated summary display. Do not cut extraction validation or ownership scope.

### Week 4 — Proactive recommendation and dogfood release

Deliver:

- One proactive `Similar problem found` card.
- Strong/possible match label, exact prior solution, age, duration, resources, and match reason.
- Replay, helpful, not-related, and dismiss actions.
- Prompt/recommendation suppression.
- Session/account deletion including embeddings.
- Network/logging audit and private dogfood package.

Exit:

- A seeded previous solution is surfaced during a genuinely similar new session without manual search.
- Dismissed matches do not repeat.
- Cross-user authorization and deletion tests pass.
- 5–10 dogfood developers can complete capture → solution → later match → replay without engineer intervention.

Cut if late: manual filters, percentages, animations, and polished summary copy. Do not cut false-positive suppression.

## Month 2 — Capture and solution-completion quality

Goal: developers create reliable solved memories with minimal interruption.

Work:

- Recruit 15–25 target developers.
- Tune deterministic candidate thresholds and inactivity timing.
- Measure candidate acceptance, dismissals, prompt deferral, final-solution completion, and privacy rejection.
- Improve only the highest-frequency capture/review failures.
- Build an opt-in labeled corpus of session boundaries and problem pairs.

Exit evidence:

- Majority of activated users record at least two solved Problem Sessions.
- Median solution capture remains under 30 seconds.
- Prompt noise is low enough that users keep capture enabled.

If users do not write final solutions, stop. Better embeddings cannot rescue an empty memory.

## Month 3 — Similarity precision

Goal: proactive matches are rare, relevant, and actionable.

Work:

- Evaluate vector, error phrase, technology, and lexical signals against hard negatives.
- Tune conservative thresholds and one-card suppression.
- Improve deterministic match explanations.
- Analyze `Helpful` and `Not related` feedback.
- Test recurrence scenarios using real previously solved sessions where time allows and controlled seeded scenarios otherwise.

Exit evidence:

- At least 70% of shown matches are helpful/opened in private beta.
- Irrelevant proactive cards stay below 0.25 per active debugging day.
- At least several users recover a solution older than seven days.

If precision misses, reduce recommendation frequency before adding model complexity.

## Month 4 — Reliability and MVP decision

Goal: decide whether engineering memory has repeat value.

Work:

- Fix the largest measured failures in capture, solution completion, or recall.
- Harden offline queue, auth expiry, provider outage, retention, deletion, backup/restore, and extension updates.
- Complete accessibility and Chrome Web Store disclosure review.
- Run a focused security review.
- Measure repeated solved-session creation and helpful recall.
- Deploy the portable Docker image and PostgreSQL to Render, then exercise health checks, backup/restore, rollback, and private database connectivity.

No new surface or integration enters month four unless it is strictly required to fix the dominant failure.

## Post-MVP candidates, evidence-gated

Choose one at a time:

1. IDE/terminal context if browser metadata repeatedly fails to identify the problem.
2. One explicit AI-conversation import if lost chat context is the dominant gap.
3. Cross-device sync if retained users work across multiple machines.
4. Curated team sharing only after personal recall retention is proven.

## Kill or rethink criteria

Rethink the product if:

- developers will not leave metadata capture enabled;
- developers do not provide final solutions even with a sub-30-second flow;
- browser metadata cannot distinguish similar problems from same-technology noise;
- proactive recommendations are too rare to drive retention or too noisy to trust;
- useful recognition requires invasive page/code/terminal capture users reject;
- users prefer current AI/IDE chat history and do not value cross-surface personal memory.

## Explicitly not on the four-month roadmap

- Chatbot or coding assistant.
- Code generation, diagnosis, or autonomous debugging.
- Repository indexing and full AI-chat ingestion.
- Notes workspace, graph, folders, dashboards, or time-saved estimates.
- Team collaboration, public sharing, billing, enterprise administration.
- Redis, WebSockets, graph database, dedicated vector database, Kubernetes, or multi-region.
