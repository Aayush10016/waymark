# Feature Prioritization

## Must have: killer loop

| Feature | Minimum form |
|---|---|
| Consent, pause, domain block | Local enforcement before upload |
| Sanitized technical browsing capture | URL/title/time/search query/resource type only |
| Conservative debugging detection | Rules first, manual start/finish escape hatch |
| Candidate Problem Session | Problem title plus ordered evidence |
| Lightweight review | Rename, short description/note, remove, dismiss/delete |
| Final-solution prompt | One user-authored answer; solved/not solved/remind later |
| AI enrichment | Summary, technologies, problem type, error phrases, one embedding |
| Similarity retrieval | User-scoped solved sessions with conservative threshold |
| Proactive memory card | One match, final solution, reason, resources |
| Replay | Open prior session and selected resources |
| Manual solved-problem search | Lexical plus semantic session search |
| Account/session deletion | Source and derived data |

## Should have after core quality

- Better candidate timing and prompt suppression.
- Editable extracted technologies and problem type.
- Feedback-driven per-user similarity threshold.
- Cross-device extension sync.
- Explicit AI-conversation import for one validated provider.

## Removed from MVP

- Chatbot, generated answers, diagnosis, or code generation.
- Repository indexing, terminal recording, IDE extension, clipboard capture.
- Full-page content, screenshots, and arbitrary DOM capture.
- Split/merge/branch editors and complex session status.
- Folders, nested tags, rich notes, documents, graph visualization.
- Team sharing, public solutions, workspace administration.
- Time-saved estimates and uncalibrated confidence percentages.
- Redis, WebSockets, graph database, object storage, microservices, and separate web app.

## Sequencing rule

Do not improve summaries, visual polish, or integrations while either condition is true:

- developers are not completing final solutions;
- proactive matches are not reliably helpful.

Those two behaviors are the product.
