# Trail Experience Specification

Status: product design specification
Version: 1.1
Audience: product design, frontend engineering, extension engineering, QA, product writing

## Product promise

**Never solve the same engineering problem twice.**

Trail should feel less like software the developer operates and more like a memory that quietly returns at the right moment.

The product has one emotional arc:

1. `Trail noticed what I was working on.`
2. `Trail preserved what actually fixed it.`
3. `Trail remembered before I repeated the work.`

## Clickable specification index

### Foundations

- [Five-minute activation](#five-minute-activation)
- [Signature interaction: Recall](#signature-interaction-recall)
- [Information architecture](#information-architecture)
- [Product principles](#product-principles)
- [Visual language](#visual-language)
- [Motion language](#motion-language)
- [AI experience](#ai-experience)
- [Global interaction rules](#global-interaction-rules)

### Screens

1. [Landing Page](#1-landing-page)
2. [Installation Flow](#2-installation-flow)
3. [Permission Flow](#3-permission-flow)
4. [First-time Onboarding](#4-first-time-onboarding)
5. [Empty States](#5-empty-states)
6. [Extension Popup](#6-extension-popup)
7. [Active Recording State](#7-active-recording-state)
8. [Problem Detection](#8-problem-detection)
9. [End Session Dialog](#9-end-session-dialog)
10. [Problem Summary](#10-problem-summary)
11. [Similar Problem Found](#11-similar-problem-found)
12. [Ghost Replay](#12-ghost-replay)
13. [Search Experience](#13-search-experience)
14. [Timeline](#14-timeline)
15. [Settings](#15-settings)
16. [Error States](#16-error-states)
17. [Offline Mode](#17-offline-mode)
18. [AI Processing States](#18-ai-processing-states)

### Flows and acceptance

- [Ghost Replay choreography](#ghost-replay-choreography)
- [Button destination map](#button-destination-map)
- [Keyboard model](#keyboard-model)
- [Responsive behavior](#responsive-behavior)
- [Accessibility](#accessibility)
- [Experience acceptance criteria](#experience-acceptance-criteria)

## Five-minute activation

Trail cannot produce a genuine personal recall before the user has recorded a solved problem. It must not fake one. The first-run experience therefore uses a clearly labeled, temporary **Demo Memory** that teaches the exact real interaction.

### Minute 0–1: Understand

The landing page shows one animated before/after:

- Before: `Spring Security CORS not working` with scattered search tabs.
- After: Trail returns the exact previous solution and decisive resource.

The page says `A demo of how Trail recalls your own solutions` so the user never mistakes synthetic content for captured data.

### Minute 1–2: Trust

Installation and permission screens explain three fields in plain language: page title, URL/domain, and allowlisted search query. A separate `Never captured` row lists page content, source code, keystrokes, forms, cookies, private hosts, and Incognito.

### Minute 2–4: Feel the product

Onboarding loads one local Demo Memory and asks the user to press **Recall**. Trail shows a simulated current CORS problem, recognizes the demo memory, and performs the real Ghost Replay interaction. The user sees the prior solution, why it matched, and the resource that ultimately mattered.

### Minute 4–5: Begin real use

The demo memory is deleted automatically. Trail enters `Watching quietly`. The user sees:

> Debug normally. When the trail goes quiet, I’ll ask what fixed it.

The onboarding ends without a tour carousel, checklist, profile setup, tags, or sample workspace.

## Signature interaction: Recall

### Name

**Recall** is Trail’s iconic interaction. **Ghost Replay** is the experience Recall opens when a matching memory exists.

### Invocation

- Click a softly pulsing Trail extension icon when a match is ready.
- Select `Recall` from the extension popup.
- Use the remappable shortcut `Ctrl/Command + Shift + .`.

### Behavior

Recall is contextual:

- **Strong current match:** opens Ghost Replay directly in the Chrome side panel.
- **Possible current match:** opens a compact comparison preview before replay.
- **No current match:** opens personal solved-problem search with the detected problem prefilled.
- **No memories yet:** explains that Recall becomes useful after the first saved solution and offers the Demo Memory.

### Signature visual

The Trail mark is normally a quiet outline. When a relevant memory exists, a second offset outline appears behind it, like an earlier version of the same path. The two outlines align over 240 ms when Recall opens. This is the **ghost lock**: the visual signal that a present problem has connected to a past solution.

### Signature sound and haptics

None by default. Developer tools should not make unsolicited sound. A future optional subtle sound is out of MVP scope.

### Why Recall is ownable

It is not a search box and not a chatbot. It is the act of pulling a verified past solution into the current problem, with one shortcut and no prompt composition.

## Information architecture

Trail has three surfaces.

### Extension popup: status and immediate action

- Is Trail recording?
- Is a Problem Session active?
- Is a memory ready?
- Start, finish, pause, block domain, or Recall.

### Chrome side panel: focused work

- End-session capture.
- Similar Problem Found.
- Ghost Replay.
- Search/Recall palette.

The side panel preserves the current webpage and avoids opening a new destination for brief interactions. Design references show it on the right; Trail respects the browser/user-configured side.

### Full-page extension app: durable memory

- Problem Summary.
- Timeline.
- Search results when expanded.
- Settings and destructive account actions.

There is no dashboard. Opening Trail defaults to Recall/search, then recent solved problems.

## Product principles

1. **Never interrupt developers while they work.** Trail signals quietly and waits for a user gesture or a genuine session boundary.
2. **AI retrieves memories instead of generating generic answers.** Recall shows verified personal solutions, never a newly generated fix.
3. **Every AI suggestion references evidence from previous sessions.** Show the exact prior errors, technologies, queries, resources, or sessions behind `Why this?`.
4. **Privacy is a feature.** Pause, exclude, edit, dismiss, retention, and deletion controls remain visible and trustworthy.
5. **Replay is faster than searching Google.** Prior solution and decisive evidence appear before browsing chronology.
6. **Users own their engineering memory.** AI never writes, changes, or upgrades the authoritative final solution.
7. **Capture automatically whenever possible.** Preserve approved context quietly without manual saving per page.
8. **Manual input remains minimal.** Ask only what automation cannot know: whether the problem was solved and what fixed it.
9. **Trail functions when AI is unavailable.** Capture, solution entry, exact search, replay, and deletion remain usable.
10. **Simplicity wins over feature count.** No assistant theater, speculative diagnosis, or feature that does not strengthen memory and recall.

## Visual language

### Character

Dark, quiet, exact, and fast. The interface should feel native beside a code editor: dense enough for experts, never visually busy.

### Color

| Token | Dark default | Use |
|---|---|---|
| Canvas | `#0B0D10` | Page and side-panel background |
| Surface | `#12151A` | Cards and elevated rows |
| Surface raised | `#181C22` | Menus, focused cards |
| Border | `#262B33` | Dividers and inactive controls |
| Text primary | `#F3F5F7` | Titles and authoritative solution |
| Text secondary | `#A8B0BC` | Metadata and supporting copy |
| Text quiet | `#707987` | Timestamps and inactive states |
| Memory violet | `#8B7CFF` | Recall, matches, focused memory |
| Solution green | `#62D49C` | User-confirmed solved state only |
| Warning amber | `#E7B45A` | Offline, possible match, deferred action |
| Error red | `#F06C75` | Destructive/failure states |

Violet never means “AI magic.” It means a connection to memory. Green is reserved for user-confirmed solutions, not model success.

### Typography

- UI: a neutral system sans with compact metrics.
- Technical strings, shortcuts, error phrases, domains, and durations: system monospace.
- Base body: 14 px in extension surfaces, 15–16 px on marketing/full-page content.
- Titles use weight, not oversized type. Side-panel title maximum is 20 px.
- Final solution is 15 px, comfortable line-height, primary color, and never italicized or rendered like AI prose.

### Shape

- Radius: 8 px controls, 10 px cards, 12 px major memory surfaces.
- Borders are preferred over shadows.
- One subtle shadow is allowed for the floating Recall palette.
- Pills are used only for technologies, match evidence, and state—not general navigation.

### Iconography

- 16 px outline icons, 1.5 px stroke.
- Trail mark: two path segments forming an offset `T`; the ghost outline appears only for Recall.
- No emoji in production UI. The rabbit may appear in brand illustration, not operational controls.

### Density

- Popup width: native compact extension width, approximately 360 px.
- Side panel: optimized for 360–480 px.
- Full page: maximum reading column 960 px; timeline content 720 px.
- Rows are 36–44 px high. Technical users should scan without scrolling through oversized cards.

### Light mode

Supported but secondary. Preserve semantic contrast and hierarchy; do not invert violet/green meaning. MVP design reviews happen dark-first and then light-mode accessibility verification.

## Motion language

### Durations

- Hover/focus: 80 ms.
- Small state change: 120 ms.
- Panel/card transition: 180 ms.
- Ghost lock and timeline reveal: 240 ms.
- Nothing exceeds 320 ms.

### Easing

- Enter: fast deceleration.
- Exit: fast acceleration.
- Reordering: standard ease-in-out.
- Avoid spring/bounce motion; it reads playful rather than precise.

### Signature motion

Ghost Replay uses an earlier, 35%-opacity version of the current path. It moves forward and aligns with the present path. The metaphor is retrieval, not haunting or time travel.

### Reduced motion

Replace translation and path drawing with 100 ms opacity changes. No meaning may depend on animation.

## AI experience

### Voice

Calm, factual, specific, and subordinate to evidence.

Use:

- `You solved something similar before.`
- `Matched the same CORS error and Spring Security.`
- `You opened this GitHub issue in the previous session.`
- `This approach appeared earlier in the current session.`
- `The previous solution came after this resource.`

Avoid:

- `I think I found your answer.`
- `I’m confident this will fix it.`
- `Here’s what you should do.`
- `Great news!`
- `91% confident` until calibration exists.
- `You saved 2 hours` without explicit evidence.

### Evidence contract

Every AI-assisted statement must map to visible evidence:

| Statement | Required evidence |
|---|---|
| Similar problem | Hybrid match cleared threshold and names overlapping signals |
| Tried this before | Same normalized query/resource/approach exists in current session |
| Previous solution came from this resource | User explicitly marked the resource decisive |
| Same error | Exact or safely normalized error phrase overlap |
| Same technology | Extracted technology overlap, shown as supporting—not sufficient—evidence |

If evidence is weak, Trail says nothing.

Temporal proximity uses narrower language: `This was the last resource opened before you saved the solution.` It never upgrades sequence into causation.

### Confidence language

- `Strong match`: strict threshold and at least one non-vector supporting signal.
- `Possible match`: lower threshold; shown only after user invokes Recall, never proactively.
- No match: no recommendation UI.

### Generated content treatment

- Generated summaries carry a small `Generated summary` label.
- User solution carries `Your solution` and solution-green confirmation.
- AI never speaks in first person.
- There is no streaming cursor or simulated thinking.

## Global interaction rules

- `Escape` closes the topmost transient surface without data loss.
- Unsaved user text persists locally through panel close, offline state, and authentication refresh.
- Destructive actions use explicit labels such as `Delete problem`, never generic `Confirm`.
- One undo toast is allowed for non-destructive removal from a session; permanent deletion requires confirmation.
- Success is shown in-place. Avoid celebratory confetti and redundant toasts.
- Tooltips explain icons and shortcuts after 400 ms hover/focus.
- Focus returns to the invoking control when a dialog or panel closes.
- Browser navigation and coding continue uninterrupted; Trail never steals focus automatically.

## Screen specification conventions

For each screen, `Primary action` is the single visually dominant action. Text links and tertiary actions remain visually quiet. `Destination` names another section in this specification.

## 1. Landing Page

### Goal

Make the promise understandable in ten seconds and demonstrate proactive recall without positioning Trail as browser history or AI chat.

### Entry

Direct URL, launch announcement, referral, or Chrome Web Store listing.

### Components

- Header: Trail mark, `How it works`, `Privacy`, `Sign in`, `Add to Chrome`.
- Hero eyebrow: `Personal engineering memory`.
- Hero headline: `Never solve the same engineering problem twice.`
- Supporting line: `Trail remembers what fixed your technical problems—and brings the solution back when they happen again.`
- Primary action: `Add to Chrome`.
- Secondary action: `Watch 42-second demo`.
- Product demo: two-state interactive frame labeled `Demo`.
- Three-step explanation: `Debug normally` → `Save what fixed it` → `Recall automatically`.
- Trust strip: `No page content`, `No source code`, `No chatbot`, `You control deletion`.
- Final CTA repeats `Add to Chrome`; no pricing table in MVP.

### Buttons and destinations

- `Add to Chrome` → Chrome Web Store, then [Installation Flow](#2-installation-flow).
- `Watch 42-second demo` → inline modal; closes to same scroll position.
- `How it works` → scroll to three-step explanation.
- `Privacy` → concise capture policy page.
- `Sign in` → email/password authentication; it is not the primary CTA.

### Default state

Demo frame shows current problem on the left and a dormant Trail mark on the right. Hover or keyboard focus reveals `Run Recall demo`.

### Empty state

Not applicable; marketing content is static and complete without user data.

### Loading state

Hero text and CTA render first. Demo uses a poster frame and loads motion afterward. Never block CTA on animation assets.

### Success state

After Web Store return, page recognizes extension installation only if available without invasive tracking and shows `Trail installed — finish setup`.

### Failure state

If Chrome is unsupported, replace CTA with `Trail currently requires desktop Chrome` and provide a non-prominent waitlist link. Do not pretend installation succeeded.

### User action and expected response

Running the demo animates a query fragment into a `Similar problem found` card, reveals the exact previous solution, and highlights one decisive resource. Total duration: 12 seconds, replayable, reduced-motion compatible.

## 2. Installation Flow

### Goal

Move from store install to informed setup without a long account funnel.

### Entry

Chrome fires extension-installed event and opens the bundled welcome page.

### Components

- Step indicator: `1 of 3`, `Install`.
- Trail mark and `Trail is installed`.
- One-sentence promise.
- Compact explanation: `Trail needs browser navigation metadata to recognize debugging sessions.`
- Primary action: `Continue`.
- Secondary action: `Remove Trail` as a quiet text link with confirmation.

### Buttons and destinations

- `Continue` → [Permission Flow](#3-permission-flow).
- `Remove Trail` → confirmation; then Chrome extension-management instruction.
- `Privacy details` → expandable in place, not a new tab.

### States

- **Default:** installed, setup incomplete.
- **Already configured:** show `Open Trail` and route to [Extension Popup](#6-extension-popup).
- **Update:** never reopen onboarding automatically; show release note only for material permission/policy change.

### Empty state

Not applicable.

### Loading state

None. All copy is bundled locally.

### Success state

Continue advances immediately; progress step updates with a 120 ms crossfade.

### Failure state

If extension storage is unavailable, show `Trail can’t finish setup because browser storage is unavailable` with `Retry` and `Remove Trail`.

### User action and expected response

User chooses Continue; no sign-up form appears yet. Trail explains permission before Chrome asks for it.

## 3. Permission Flow

### Goal

Earn trust before requesting sensitive browser access.

### Entry

Installation step 2.

### Components

- Step indicator: `2 of 3`, `Permission`.
- Two-column disclosure:
  - `Trail records`: page title, sanitized URL/domain, allowlisted search query, time and navigation relationship.
  - `Trail never records`: page content, code, keystrokes, forms, cookies, private/local hosts, Incognito.
- Small diagram: Browser → local sanitizer → Trail account.
- `Why this is needed` accordion with direct examples.
- Domain exclusion input available after permission, not before the browser prompt.
- Primary action: `Allow Trail`.
- Secondary action: `Not now`.

### Buttons and destinations

- `Allow Trail` → native Chrome permission prompt; on grant → [First-time Onboarding](#4-first-time-onboarding).
- `Not now` → limited [Empty States](#5-empty-states), capture disabled.
- `Read full privacy policy` → external first-party policy tab.

### Default state

No native prompt appears until the user clicks `Allow Trail`.

### Empty state

If permissions are not granted, Trail remains usable only for Demo Memory and settings; it does not show a broken library.

### Loading state

After click, button reads `Waiting for Chrome…`; no spinner elsewhere.

### Success state

Permission rows receive a small check. Copy changes to `Access granted. Trail still ignores excluded data.` Then advance.

### Failure state

- **Denied:** `Permission wasn’t granted. Trail cannot recognize debugging sessions without navigation metadata.` Actions: `Try again`, `Use demo only`, `Remove Trail`.
- **Partially granted/removed later:** list the missing capability specifically; do not loop the prompt.

### User action and expected response

Granting permission never begins capture until onboarding consent is completed. The UI states this explicitly.

## 4. First-time Onboarding

### Goal

Deliver the five-minute wow, complete identity/consent, and leave the user in a calm ready state.

### Entry

Permission granted or user chooses demo-only mode.

### Components

One progressive surface with three short scenes, not a swipe carousel.

#### Scene A: Meet Recall

- Heading: `Meet Recall`.
- Copy: `When a current problem resembles one you solved before, Trail brings back your exact solution.`
- Demo current problem: `Spring Security CORS blocked localhost:3000`.
- Primary action: `Try Recall`.

#### Scene B: Demo Ghost Replay

- Side panel opens in Demo mode.
- Strong-match card reveals prior solution and why it matched.
- Primary action: `Replay demo solution`.
- After replay: `This was a demo. Your memories will always come from solutions you saved.`

#### Scene C: Start Trail

- Email/password registration or sign-in if not complete.
- Consent checkbox is not preselected: `Capture sanitized debugging metadata under the privacy policy.`
- Optional domain exclusions with examples such as employer/admin domains.
- Primary action: `Start Trail`.

### Buttons and destinations

- `Try Recall` → Demo [Similar Problem Found](#11-similar-problem-found).
- `Replay demo solution` → Demo [Ghost Replay](#12-ghost-replay).
- `Start Trail` → [Empty States](#5-empty-states), ready state.
- `Skip demo` → Scene C.
- `Use Trail without capture` → library/search in disabled state.

### States

- **Demo:** persistent `Demo Memory` label; never mixed with real sessions.
- **Signed out:** Scene C contains email and password fields, `Create account`, `Sign in`, and `Forgot password`; no social-login buttons.
- **Unverified email:** show a neutral verification-required state with `Resend email`; do not begin server capture until verified.
- **Returning account:** authentication completes and existing memories load after consent.

### Empty state

After setup, show `Watching quietly` rather than an empty dashboard.

### Loading state

During authentication: inline `Connecting your private memory…`. The Demo Memory remains local and interactive.

### Success state

Demo memory dissolves from the list with copy `Demo removed`. Trail mark switches from demo violet to neutral recording outline. Final copy: `Debug normally. Trail will stay out of the way.`

### Failure state

Pairing failure preserves progress and offers `Try again` and `Continue in demo mode`. Never restart permissions or replay.

### User action and expected response

Completing Start Trail closes onboarding and opens the popup once. Focus lands on `Start problem`; capture begins only after consent acknowledgment is stored.

## 5. Empty States

### Goal

Make absence feel intentional, explain the next value-producing action, and avoid dashboard scaffolding.

### Variants

#### Ready, no Problem Sessions

- Title: `Your engineering memory starts with the next fix.`
- Copy: `Debug normally. When the session ends, Trail will ask what solved it.`
- Primary action: `Start a problem`.
- Secondary: `Replay demo`.
- Small status: `Recording on`.

#### Candidate active, no solved memories

- Title: `First problem in progress`.
- Shows inferred title and resource count.
- Action: `Finish problem`.
- Does not promise similarity yet.

#### Solved memories exist, no search result

- Title: `No solved problem matches “…”`.
- Suggestions: remove framework version, paste exact error phrase, search a technology.
- Action: `Clear search`.
- Never offer a web answer or chatbot.

#### Capture disabled

- Title: `Trail is paused`.
- Copy explains no navigation is being recorded.
- Primary action: `Resume capture`.

#### Demo-only mode

- Title: `Capture is not enabled`.
- Primary action: `Review permissions`.
- Demo remains available but visually separated.

### Components

One quiet illustration made from two offset paths, one headline, one explanatory sentence, one primary action, maximum one secondary link.

### Buttons and destinations

- `Start a problem` → [Active Recording State](#7-active-recording-state).
- `Replay demo` → Demo [Ghost Replay](#12-ghost-replay).
- `Resume capture` → recording-ready state in place.
- `Review permissions` → [Permission Flow](#3-permission-flow).
- `Clear search` → recent solved problems in [Search Experience](#13-search-experience).

### Default state

Use the variant matching actual system state. Never show multiple empty-state messages simultaneously.

### Empty state

This screen is the empty-state system itself. Each variant must describe the missing value, explain what Trail will do next, and provide one action that creates or restores value.

### Loading state

Never show an empty state before the initial data read completes. Use three skeleton rows for up to 400 ms; after that show explicit loading copy.

### Success state

The first saved solution replaces the empty state in place with the new Problem Summary; do not redirect to a dashboard.

### Failure state

Data-load failures use [Error States](#16-error-states), not empty-state copy.

### User action and expected response

Every action leads directly to capture, permission recovery, demo, or cleared search. No generic `Learn more` dead ends.

## 6. Extension Popup

### Goal

Answer `What is Trail doing right now?` and provide the one correct next action.

### Components

- Header: Trail mark, state label, settings icon.
- Current domain row with exclusion affordance.
- Context card occupying most of the popup.
- Footer: pause/resume and `Open Trail`.

### Context priority

Only the highest-priority context appears:

1. Similar memory ready.
2. Session awaiting solution.
3. Problem Session active.
4. Recording idle.
5. Offline/sync issue.
6. Paused/permission missing.

### Buttons and destinations

- `Recall` → [Ghost Replay](#12-ghost-replay) or [Search Experience](#13-search-experience).
- `What solved it?` → [End Session Dialog](#9-end-session-dialog).
- `Finish problem` → [End Session Dialog](#9-end-session-dialog).
- `Start problem` → [Active Recording State](#7-active-recording-state).
- `Block this domain` → inline confirmation; future events excluded immediately.
- `Pause`/`Resume` → same popup state update.
- `Open Trail` → [Search Experience](#13-search-experience), full-page when expanded.
- Settings icon → [Settings](#15-settings).

### Default state

`Watching quietly` with small text `No active problem detected`. Primary action is `Start problem`; it is not styled urgently.

### Empty state

First-use popup shows the ready-no-sessions variant from [Empty States](#5-empty-states).

### Loading state

Render local recording state instantly. Server-dependent context card uses a fixed-height skeleton for no more than 400 ms, then `Checking memory…`.

### Success state

Actions update in place. Starting a problem changes the context card to active without closing the popup.

### Failure state

Show one-line cause and one action. Example: `Capture is local; sync will retry when online.` Do not stack multiple banners.

### User action and expected response

Popup never auto-opens. When opened, focus begins on the primary context action, then follows visual order.

## 7. Active Recording State

### Goal

Confirm that Trail understands a debugging session without demanding attention or exposing a surveillance-style event feed.

### Entry

Manual `Start problem` or conservative automatic detection.

### Components

- Trail icon: thin violet outer ring, static rather than pulsing.
- Popup state: `Problem session active`.
- Inferred title, max two lines.
- Metadata: start time and count by broad source type, for example `3 docs · 2 discussions · 1 repo`.
- Primary action: `Finish problem`.
- Secondary: `Not a problem`.
- Quiet link: `View captured context`.

### States

- **Automatically detected:** label `Detected from debugging activity`; `Not a problem` visible.
- **Manually started:** label `Started by you`; manual boundary has precedence.
- **Low context:** title `Technical problem` until evidence supports a better deterministic title.
- **Paused mid-session:** session remains open; new events stop; state says `Paused`.

### Buttons and destinations

- `Finish problem` → [End Session Dialog](#9-end-session-dialog).
- `Not a problem` → idle popup with five-second Undo.
- `View captured context` → compact candidate preview.
- `Resume capture` when paused → active state in place.

### Empty state

No resource details yet: `Waiting for the first technical page or search.`

### Loading state

Title refinement uses a subtle 120 ms text crossfade only after the current title is stable for at least two seconds. Never animate on each page.

### Success state

Additional resources update counts silently. There is no counter badge on every event.

### Failure state

If sync fails, state becomes `Recording locally` with amber dot. `Finish problem` remains usable and solution text is stored locally.

### User action and expected response

- `Finish problem` closes the capture boundary and opens End Session.
- `Not a problem` dismisses the candidate, suppresses the prompt, and offers five-second `Undo`.
- `View captured context` opens a compact ordered preview, not the full Timeline.

## 8. Problem Detection

### Goal

Signal that Trail recognized debugging while honoring the principle never to interrupt active work.

### Trigger

Deterministic debugging threshold clears. AI may later refine the title but does not trigger an interrupt.

### Presentation

- No modal, notification, sound, injected webpage UI, or focus change.
- Extension icon switches from neutral outline to a static violet-ring active variant. Browser-toolbar chrome does not rely on frame-by-frame animation.
- On first detection only, badge shows a tiny dot for three seconds, then removes it.
- Popup, if opened, shows Active Recording.

### Components

- In popup: inferred title, reason disclosure, `Finish problem`, `Not a problem`.
- `Why detected?` expands to evidence such as `3 related searches`, `Spring Security documentation`, `same CORS terms`.

### Buttons and destinations

- `Finish problem` → [End Session Dialog](#9-end-session-dialog).
- `Not a problem` → dismiss and return popup to idle.
- `Why detected?` → inline evidence; never model scores.

### Default state

The only persistent signal is the violet ring on the extension icon. All explanatory content waits inside the user-opened popup.

### Empty state

If Trail lacks a stable title, use `Technical problem detected`; do not fabricate specificity.

### Loading state

No visible AI loading at detection. Deterministic state appears immediately.

### Success state

The developer continues browsing with no further UI until finish/inactivity.

### Failure state

False detection is recoverable with one click. After dismissal, identical signals do not recreate a candidate for 30 minutes unless manually started.

### User action and expected response

Ignoring detection has no penalty. Trail waits for a session boundary and does not increase prompt urgency.

## 9. End Session Dialog

### Goal

Capture the only fact Trail cannot infer: whether the problem was solved and what fixed it.

### Entry

Manual finish, inactivity boundary, or popup action from `Awaiting solution`.

### Presentation

The side panel opens only after a user gesture. Automatic inactivity changes the extension badge to a small solution-green check-dot; it never opens the panel itself.

### Components

- Problem title and compact session context.
- Outcome choice.
- Final-solution field after `Solved`.
- Collapsed captured-context review.
- Primary save action, secondary review/back actions, and safe deferral/dismissal.

### Step 1: Outcome

- Title: `Did you solve it?`
- Inferred problem title and compact session metadata.
- Primary action: `Solved`.
- Secondary actions: `Not yet`, `Not a problem`.
- Tertiary: `Ask me later`.

### Step 2: Final solution

- Prompt: `What finally solved it?`
- Multiline field, auto-focused only because the user chose `Solved`.
- Placeholder: `Allowed localhost:3000 in CorsConfiguration and placed the JWT filter before UsernamePasswordAuthenticationFilter.`
- Character guidance: `One or two sentences is enough.` Not a hard minimum beyond meaningful non-empty text.
- Context preview collapsed below.
- Primary action: `Save solution`.
- Secondary: `Review context`.
- Back: returns to outcome without clearing text.

### Buttons and destinations

- `Solved` → Step 2.
- `Save solution` → [Problem Summary](#10-problem-summary).
- `Review context` → inline artifact review/removal.
- `Not yet` → keep unresolved only after `Keep for later`; otherwise short retention.
- `Ask me later` → close panel, one reminder allowed.
- `Not a problem` → dismiss with undo.

### Default state

No outcome preselected. AI does not suggest final-solution text.

### Empty state

If session has no usable resources, the final-solution flow still works. Copy: `There isn’t much captured context, but your solution can still be remembered.`

### Loading state

Saving writes locally first. Button changes to `Saving…` with stable width. Panel cannot be accidentally closed by outside click while the write is pending, but Escape stores a draft and closes.

### Success state

The solution field compresses into a read-only card with solution-green check and `Saved to your engineering memory`. After 700 ms, transition to Problem Summary. No confetti.

### Failure state

- **Offline:** `Saved on this device. Trail will sync when online.` Treat as successful locally.
- **Server rejection:** preserve draft; show exact safe reason and `Retry`.
- **Auth expired:** preserve draft; `Sign in again` opens authentication without losing content.

### User action and expected response

The interaction requires one outcome choice, one short text entry, and one save. No tags, classification, rating, or resource curation is required.

## 10. Problem Summary

### Goal

Present a solved problem as a compact, trustworthy memory whose solution can be understood in seconds.

### Entry

Immediately after saving, from search, from Ghost Replay, or from recent memories.

### Components

1. Header: solved check, problem title, date, previous duration.
2. `Your solution` card in primary contrast.
3. Matchable context: technologies, problem type, normalized error phrases.
4. `Decisive resources` if evidence exists.
5. Generated problem summary, labeled.
6. Collapsed Timeline preview.
7. Overflow menu: edit title/description/note, delete.

The final solution always appears above generated content and browsing evidence.

### Buttons and destinations

- `Replay resources` → [Ghost Replay](#12-ghost-replay) in historical mode.
- `View timeline` → [Timeline](#14-timeline).
- Resource row → open URL in a new tab; preserve Summary.
- `Edit solution` → inline edit with authorship label retained.
- `Delete problem` → explicit destructive confirmation.
- `Back` → prior search/Recall context, preserving query and scroll.

### Default state

Title and solution are fully visible; metadata remains compact. Technologies show a maximum of five before `+N`.

### Empty state

- No decisive resource: omit section; do not say `No resources` unless user expands Timeline.
- No generated summary: deterministic title/solution remain sufficient.
- No technologies: show no empty chip row.

### Loading state

Authoritative title/solution render immediately. Derived fields use stable skeleton lines in their exact eventual positions. Never skeleton the user’s solution after it is locally available.

### Success state

After edit, the solution card returns to read-only and shows a quiet `Saved` for two seconds. Re-embedding status stays backstage unless it fails.

### Failure state

Derived-data failure shows `Some matching details are unavailable` inside metadata area with `Retry`. The solved memory itself remains normal.

### User action and expected response

Opening a resource marks it as replayed locally/server-side for recommendation feedback but does not reorder historical evidence.

## 11. Similar Problem Found

### Goal

Deliver the core promise in one glance without claiming the old solution will fix the new problem.

### Entry

User invokes Recall after the Trail icon signals a strong match. Possible matches appear only when Recall is explicitly invoked.

### Components

- Eyebrow: `Similar problem found`.
- Match label: `Strong match` or `Possible match`.
- Age: `Solved 183 days ago`.
- Prior problem title.
- `Why this appeared` evidence row, maximum three items.
- `Your previous solution` with exact user text.
- Representative resource list, maximum three.
- Previous duration labeled `Previous session`, never `Time saved`.
- Primary action: `Replay solution`.
- Secondary: `Open problem`.
- Feedback: `Helpful`, `Not related`, dismiss.

### Buttons and destinations

- `Replay solution` → [Ghost Replay](#12-ghost-replay).
- `Open problem` → [Problem Summary](#10-problem-summary).
- Resource → opens new tab while panel stays available.
- `Helpful` → records feedback, changes to selected state.
- `Not related` → suppresses match and asks no follow-up survey.
- Dismiss → closes panel and suppresses this pair for current session.

### Default state

Prior solution is fully shown unless longer than six lines; then show first six with `Show full solution`. Do not hide the value behind a click for normal answers.

### Empty state

No match means no Similar Problem screen. Recall routes to Search instead.

### Loading state

The side panel first shows current detected problem and `Checking your solved problems…`. If lookup exceeds 1.5 seconds, show `You can keep working; Trail will update here.` Never use a conversational typing indicator.

### Success state

The ghost lock animation completes, then content reveals in this order: match reason, title, solution, resources. Total under 320 ms after data is ready.

### Failure state

`Memory lookup is unavailable` with `Search manually` and `Try again`. Do not show a stale or low-confidence recommendation as fallback.

### User action and expected response

The first click on `Replay solution` transforms the same side panel; it does not navigate away or open all tabs.

## 12. Ghost Replay

### Goal

Bring the previous solution into the current debugging context and make the decisive evidence immediately actionable.

### Entry

`Replay solution` from Similar Problem Found, Problem Summary, or search result.

### Components

- Pinned header: `Ghost Replay`, prior problem title, age, close.
- Present/past context comparison.
- `Your previous solution` card, expanded.
- Match evidence row.
- `Decisive resource` card if known.
- Condensed prior path with three modes:
  - `Solution` default;
  - `Key resources`;
  - `Full trail`.
- Progress marker showing which old resources were reopened now.
- Primary action: `Open decisive resource` or `Open first resource`.
- Secondary: `Mark helpful`.
- Footer action: `Return to current problem`.

### Buttons and destinations

- `Open decisive resource` → opens one new tab; side panel remains pinned.
- `Key resources` → expands maximum five high-signal resources.
- `Full trail` → [Timeline](#14-timeline) within side panel or full-page expansion.
- `Open next` → opens the next selected historical resource only.
- `Already tried` → user assertion attached to current session; does not modify old memory.
- `Mark helpful` → feedback success state.
- `Return to current problem`/close → closes side panel and returns focus to originating tab.

### Default state

Solution text and one decisive resource appear. Full chronology is collapsed. Ghost Replay never begins by opening tabs automatically.

### Empty state

If resources are dead or absent, the exact solution remains replayable. Copy: `The previous resources are unavailable; your saved solution is intact.`

### Loading state

Historical content already present in the match payload renders immediately. Additional timeline details load below without shifting the solution card.

### Success state

Opening a resource draws one short violet connector from its historical step to a `Opened now` marker. `Helpful` becomes `Helped` with solution-green check.

### Failure state

- Unsafe/unsupported URL: row disabled with `Can’t open this URL safely`.
- Dead link: browser failure cannot be known reliably in MVP; do not pre-label without evidence.
- Popup/tab failure: `Chrome blocked the tab. Open again`.

### User action and expected response

Ghost Replay is a reference, not an automation. It never changes code, settings, tabs, or current Problem Session without an explicit click.

## 13. Search Experience

### Goal

Provide instant manual access to solved problems without becoming a general browser-history search or chatbot.

### Entry

Recall with no strong match, `Open Trail`, keyboard shortcut without current context, or full-page app.

### Components

- Recall field with search icon and shortcut hint.
- Placeholder: `Search problems, errors, technologies, or solutions…`.
- Optional current-context chip: `Current: Spring Security CORS` with remove.
- Result list of solved Problem Sessions.
- Each result: title, solution excerpt, date, technologies, one match explanation.
- Recent solved problems below the empty query.
- `Open full library` when invoked from side panel.

### Query behavior

- Search begins after first meaningful token with 150 ms debounce.
- Exact error/solution matches outrank semantic similarity.
- Results never include raw ungrouped browsing events.
- Search is not phrased as a question-answer exchange.
- Pasting a multiline error collapses it to a one-line query visually while preserving the full query for matching.

### Buttons and destinations

- Result row → [Problem Summary](#10-problem-summary).
- Result `Replay` quick action → [Ghost Replay](#12-ghost-replay).
- Current-context chip remove → rerun without detected context.
- `Clear` → recent solved problems.
- `Open full library` → same state in full-page extension app.

### Default state

Empty query shows up to five recent solved problems and one line: `Recall searches your solutions, not the web.`

### Empty state

`No solved problem matches “…”` plus query suggestions and `Clear search`. No `Ask AI`, web search, or create-note prompt.

### Loading state

Keep previous results at 50% opacity with a top progress line after 250 ms. Do not blank the list between keystrokes.

### Success state

Matched substrings/evidence are highlighted subtly. Keyboard focus moves through results without changing query focus until arrow keys are used.

### Failure state

Lexical fallback results display with note `Semantic matching is temporarily unavailable`. If all search fails, preserve query and offer `Retry`.

### User action and expected response

Enter opens the selected result; `Shift+Enter` starts Ghost Replay for that result; Escape first clears selection, then closes transient Recall.

## 14. Timeline

### Goal

Show trustworthy chronology when the developer needs provenance, without making the browsing trail the primary product.

### Entry

Problem Summary `View timeline`, Ghost Replay `Full trail`, or candidate `Review context`.

### Components

- Sticky compact header: problem title, date, duration.
- Solution anchor at the end/top depending mode, clearly user-authored.
- Vertical ordered list grouped by natural time gaps, not source categories.
- Artifact row: time, type icon, title/query, domain, optional decisive marker.
- Search-query rows use monospace and retain sequence.
- Filter toggles: `All`, `Searches`, `Resources`; no complex filters.
- `Collapse repeats` on by default for adjacent duplicates.

### Modes

- **Historical:** immutable evidence; no reordering.
- **Candidate review:** rows can be removed with undo.
- **Ghost Replay:** rows already reopened now receive a violet `Opened now` marker.

### Buttons and destinations

- Artifact → open new tab after URL safety validation.
- `Mark decisive` → user-only action; at most one primary decisive resource, reversible.
- `Remove` in candidate mode → local undo for five seconds.
- `Back to solution` → [Problem Summary](#10-problem-summary) or Ghost Replay origin.

### Default state

Chronology is collapsed to meaningful events. Repeated reloads and redirects are visually grouped with `+N repeats`.

### Empty state

`No browsing context was saved for this problem.` The solution remains visible and primary.

### Loading state

Header/solution render first. Timeline skeleton uses fixed-height rows and appends progressively without jumping scroll position.

### Success state

Opening/marking a resource updates the row in place. Historical order never changes.

### Failure state

If some artifacts cannot load, show a bounded gap row: `3 events unavailable` with `Retry`. Never reorder around missing events.

### User action and expected response

Timeline keyboard navigation uses up/down through rows, Enter to open, and `D` to mark decisive when row focus is active and shortcut help is visible.

## 15. Settings

### Goal

Give users direct control over capture, AI processing, exclusions, data, account, and shortcuts without exposing engineering internals.

### Components

1. **Capture:** on/off, status, manual start/finish shortcut.
2. **Excluded domains:** add, remove, match subdomains; clear explanation that exclusion happens before upload.
3. **AI and memory:** concise list of AI functions and fields sent; no generic `AI on/off` if disabling it makes recall impossible. Offer `Pause AI processing` only if product behavior is clearly explained.
4. **Notifications:** badge only by default; no browser notifications in MVP.
5. **Shortcuts:** Recall, start/finish problem; link to Chrome shortcut management.
6. **Data:** solved-problem count, delete individual via library, delete all/account.
7. **Account:** identity, reconnect/sign out.
8. **About:** version, policy version, privacy, support.

### Buttons and destinations

- `Pause capture` → popup and all surfaces update immediately.
- `Add excluded domain` → inline validated entry.
- `Delete all data` → typed/account-reauth destructive flow.
- `Sign out` → queue warning if unsynced, then revoke/clear.
- `View privacy details` → first-party policy.

### Default state

Compact sections with direct values. No dashboard cards or usage graphs.

### Empty state

Excluded domains: `No custom exclusions. Trail still blocks private and prohibited addresses automatically.`

### Loading state

Local capture/exclusion state renders immediately. Account/data counts show skeleton values without disabling unrelated settings.

### Success state

Settings save immediately and show a two-second inline check. Domain exclusion states `Blocked before capture`.

### Failure state

Local-control failure is high priority: show persistent red inline message and do not claim pause/block succeeded. Server-only preference failure can retry without reverting local safety state.

### User action and expected response

Destructive controls are isolated at the bottom. `Delete all data` summarizes exactly what disappears, backup expiry, and session revocation before final confirmation.

## 16. Error States

### Goal

Explain what failed, what remains safe, and the single next action without blaming the user or exposing internals.

### Error hierarchy

1. Privacy/capture correctness.
2. User-authored solution durability.
3. Authentication/sync.
4. Similarity/enrichment.
5. Cosmetic metadata.

### Global error pattern

- Plain title.
- One-sentence consequence.
- State what is preserved.
- One primary recovery action.
- Optional diagnostic code behind `Details`; no raw content.

### Components

- Status icon and plain-language title.
- Consequence/preservation sentence.
- One primary recovery button.
- Optional `Details`, `Dismiss`, and support link when safe.

### Buttons and destinations

- Recovery actions operate in place or route to the named permission, settings, search, or reconnect flow.
- `Copy diagnostic ID` copies only an opaque identifier.
- `Dismiss` returns focus to the failed control and is absent for unresolved capture-safety failures.

### Default state

Errors are scoped to the smallest affected component. Use a full-page error only when no safe primary content can render.

### Required variants

| Error | Copy direction | Primary action |
|---|---|---|
| Permission removed | `Trail can’t record navigation until access is restored.` | `Restore access` |
| Local storage full | `Recording paused to avoid losing new context.` | `Manage storage` |
| Authentication expired | `Your local draft is safe. Reconnect to sync.` | `Reconnect` |
| Sync unavailable | `Recording continues on this device.` | `Retry now` |
| AI enrichment failed | `Your solution is saved; matching details are delayed.` | `Retry processing` |
| Similarity unavailable | `No recommendation was shown. Search still works by exact text.` | `Search memory` |
| Unsafe URL | `Trail blocked this link because it is not a safe web address.` | None |
| Account deletion failed | `Access is revoked; deletion will retry.` | `View status` |

### Empty state

Errors never masquerade as empty content.

### Loading state

Recovery actions show progress only on the affected control. Other safe actions remain available.

### Success state

Error collapses in place to `Recovered` for two seconds, then returns to normal UI.

### Failure state

After repeated retry, offer `Copy diagnostic ID` and support link. Never expose URLs, queries, solutions, tokens, or provider messages.

### User action and expected response

Dismiss is available only when continued use is safe. Capture correctness errors remain persistent until resolved or capture is explicitly disabled.

## 17. Offline Mode

### Goal

Preserve debugging context and final-solution text without making the user manage synchronization.

### Components

- Amber status dot and `Offline` label in popup/header.
- Queue state in plain language: `Recording on this device`.
- Unsynced solved session label: `Saved locally`.
- Settings diagnostic: event count/oldest age only, never URLs.

### Behavior

- Capture continues into a bounded local queue.
- Manual start/finish works.
- End Session and final-solution entry work fully.
- Previously cached solved memories and lexical search remain available.
- New semantic recommendations that require the server do not run.
- Replay of already stored URLs works if the browser can reach them.

### Default state

Offline status is quiet and persistent. It does not cover the page or reopen the popup; the next user-opened Trail surface explains local recording.

### Buttons and destinations

- `View local session` → local Problem Summary.
- `Retry now` → immediate bounded sync attempt.
- `Pause capture` → local state change.

### Empty state

No cached memories: `Your current session is safe here. Similarity returns when Trail reconnects.`

### Loading state

On reconnect, status becomes `Syncing…` with count-free progress. Do not show a per-event upload feed.

### Success state

`All caught up` for two seconds, amber dot fades to neutral. Locally saved solutions receive server IDs without changing visible order/content.

### Failure state

Queue pressure: `Local recording space is nearly full.` Primary action `Review options`; never evict a user-authored final solution. Oldest ungrouped events may expire according to disclosed policy.

### User action and expected response

Closing the browser does not lose acknowledged local drafts. If sign-out would remove unsynced data, Trail requires explicit confirmation and states exactly what will be lost.

## 18. AI Processing States

### Goal

Make asynchronous work legible when necessary without turning AI into a character or blocking the core workflow.

### State model

| State | User-facing treatment |
|---|---|
| Not started | No label unless detail requires it |
| Queued | `Preparing matching details…` |
| Extracting | Same label; no stage theater |
| Embedding/indexing | `Making this solution recallable…` |
| Ready | Label disappears; derived metadata fades in |
| Delayed/offline | `Your solution is saved. Matching will finish later.` |
| Failed | `Matching details unavailable` with Retry |

### Components

- Small inline status below generated metadata area.
- Stable placeholders for technologies/summary.
- `Your solution` remains fully interactive.
- Optional `Why processing?` disclosure explains summary/extraction/embedding in plain language.

### Buttons and destinations

- `Retry` → enqueue processing again; button disables during request.
- `Why processing?` → inline explanation, not model/provider jargon.
- `Delete problem` remains available in every state.

### Default state

Most users should see no AI state because processing completes asynchronously after save. Do not expose token counts, model names, queue numbers, or percentages.

### Empty state

Insufficient context: omit generated summary/technology sections and show only on detail: `Not enough context to add matching details.` This is a valid result, not an error.

### Loading state

No streaming text. Use fixed skeleton chips/lines and a subtle violet progress line only after 500 ms.

### Success state

Derived fields fade in over 120 ms. No toast. If the user is editing, defer visual insertion until focus leaves the field.

### Failure state

The failure is scoped to recall quality. Copy must confirm: `Your problem and solution are saved.` Retry is optional; the product remains usable with exact search.

### User action and expected response

The user never waits on this screen to finish saving. Navigating away is always safe.

## Ghost Replay choreography

This sequence is normative. Animation timing may be tuned during implementation, but order, focus behavior, evidence, and user control may not change without product review.

### Phase 0: Recognition happens backstage

1. Current debugging activity crosses the conservative threshold.
2. Trail compares the bounded current context with the user’s solved Problem Sessions.
3. A prior session clears the strong-match threshold and has supporting evidence.
4. The extension icon switches to the static offset ghost-outline variant.
5. A small violet dot remains until the user invokes Recall or the current session ends.
6. No browser notification, toolbar animation, sound, modal, webpage injection, or focus change occurs.

### Phase 1: User invokes Recall

Invocation is one of:

- click the Trail icon, then `Recall`;
- use `Ctrl/Command + Shift + .`;
- click `Recall` from the full-page app.

Expected response:

1. Chrome side panel opens at the user-configured browser edge; design references assume the right edge.
2. Focus lands on the panel heading, not inside the webpage.
3. Current page remains visible and untouched.
4. The Trail mark renders with present and ghost outlines separated by 4 px.
5. The ghost outline slides forward and aligns with the present outline over 240 ms.
6. At alignment, the heading changes from `Recall` to `Similar problem found` with a 120 ms opacity transition.

Reduced motion: side panel opens normally and the mark switches states with a 100 ms crossfade.

### Phase 2: Explain the match

The panel reveals content top-to-bottom in three 60 ms staggered groups:

1. `Strong match · Solved 183 days ago`.
2. Prior problem title and evidence chips.
3. Exact previous solution.

Evidence chips use concrete language:

- `Same error: CORS policy blocked…`
- `Spring Security`
- `JWT filter`

Hover/focus on a chip opens a tooltip with the current and prior matched snippets. Snippets are escaped, bounded, and never reveal hidden page content.

The previous solution is labeled `Your previous solution`. It is never animated as generated text and never appears word-by-word.

### Phase 3: User decides

Available actions:

- `Replay solution` — primary.
- `Open problem` — secondary.
- `Not related` — quiet negative feedback.
- Close — dismissal for current session.

Keyboard:

- Enter activates `Replay solution`.
- Arrow down moves through secondary actions/resources.
- Escape closes and returns focus to the originating browser tab.

If the user chooses `Not related`:

1. Card compresses vertically over 120 ms.
2. Copy changes to `Dismissed for this problem.`
3. Panel closes after 500 ms unless focus is inside it.
4. The ghost outline disappears from the extension icon.
5. The same prior/current pair cannot reappear during this session.

### Phase 4: Enter Ghost Replay

After `Replay solution`:

1. Similar Problem card does not navigate away; it transforms in place.
2. Header condenses to `Ghost Replay` with prior problem title beneath.
3. The previous solution card moves to the top and expands to full text over 180 ms.
4. A thin vertical ghost path draws downward over 240 ms.
5. At most three key historical resources appear along the path.
6. The resource most closely associated with solution capture receives `Decisive resource` only if evidence supports that label; otherwise use `Last relevant resource` or no special label.
7. Primary action becomes `Open decisive resource` or `Open first resource`.
8. Focus moves to the solution heading, then the primary resource button is next in tab order.

There is no celebratory animation and no automatic tab opening.

### Phase 5: Replay one resource

When the user clicks `Open decisive resource`:

1. Trail validates the URL scheme locally.
2. A new tab opens beside the current tab.
3. The side panel remains open and pinned.
4. The resource row gains a violet dot and `Opened now` label.
5. A 120 ms connector animates from the historical step to the label.
6. Primary action changes to `Open next resource` if another key resource exists; otherwise `Return to current problem`.
7. Trail records only that the historical resource was opened, not what the user does on the page.

If Chrome blocks the tab, no connector appears. The button returns with `Open again` and an inline error.

### Phase 6: Recognize repeated attempts

If exact evidence shows the developer already opened the same resource or used the same normalized query in the current session, the resource row says:

> `Already visited in this session`

If a prior approach appears in current queries/titles but cannot be proven as attempted, Trail says:

> `This approach also appeared earlier today`

Trail must not say `You already tried this` based only on semantic similarity. The user may click `Already tried`, which becomes explicit current-session evidence.

### Phase 7: Complete replay

The user can finish by:

- clicking `Helped`;
- clicking `Return to current problem`;
- pressing Escape;
- closing the side panel.

On `Helped`:

1. The control changes to a solution-green check and `Helped` over 120 ms.
2. Copy below says `This memory will be easier to recognize next time.`
3. Do not ask for a rating or comment.
4. The panel stays open until the user closes it or selects Return.

On Return:

1. Ghost path fades to 0 over 120 ms.
2. Header mark separates briefly into past/present outlines, then the past outline fades.
3. Side panel closes.
4. Focus returns to the tab that invoked Recall, not necessarily the most recently opened resource.
5. The active current Problem Session continues recording unless the user finishes it.

### Phase 8: After the current problem ends

When the user later records the new final solution, Trail may show one factual line in Problem Summary:

> `A previous solution was replayed during this session.`

It must not infer that the previous solution fixed the new problem unless the user explicitly says so.

## Button destination map

This table is the routing contract for frontend implementation.

| Source | Control | Destination/response |
|---|---|---|
| Landing | Add to Chrome | Chrome Web Store → Installation |
| Landing | Watch demo | Inline demo modal |
| Installation | Continue | Permission Flow |
| Permission | Allow Trail | Native prompt → Onboarding |
| Onboarding | Try Recall | Demo Similar Problem Found |
| Onboarding | Replay demo solution | Demo Ghost Replay |
| Onboarding | Start Trail | Ready empty state/Popup |
| Empty state | Start a problem | Active Recording |
| Popup | Start problem | Active Recording |
| Popup/Active | Finish problem | End Session |
| Popup | Recall | Similar Problem, Ghost Replay, or Search based on context |
| Popup | Block this domain | Inline exclusion confirmation |
| End Session | Solved | Final-solution step |
| End Session | Save solution | Problem Summary |
| Similar Problem | Replay solution | Ghost Replay |
| Similar Problem | Open problem | Problem Summary |
| Search | Result row | Problem Summary |
| Search | Replay quick action | Ghost Replay |
| Problem Summary | View timeline | Timeline |
| Problem Summary | Replay resources | Ghost Replay historical mode |
| Ghost Replay | Full trail | Timeline replay mode |
| Ghost Replay | Return | Close panel and restore origin focus |
| Any durable view | Delete problem | Destructive confirmation → prior list/search |
| Settings | Delete all data | Reauthentication + destructive confirmation |

## Keyboard model

| Shortcut | Action | Context |
|---|---|---|
| `Ctrl/Command + Shift + .` | Recall | Global, remappable |
| `Ctrl/Command + Shift + S` | Start/finish Problem Session | Global, remappable; final assignment validated against Chrome conflicts |
| `/` | Focus search | Full-page Trail only, not browser page |
| `Enter` | Activate selected primary/result | Popup, side panel, full page |
| `Shift + Enter` | Ghost Replay selected search result | Search |
| `Escape` | Close top transient/return focus | All transient surfaces |
| `↑` / `↓` | Navigate results/timeline rows | Search and Timeline |
| `D` | Mark focused resource decisive | Timeline only when shortcut help is shown |
| `?` | Open shortcut help | Extension app/panel |

Rules:

- Never override browser/page shortcuts from a content script.
- Global extension shortcuts are user-remappable.
- All actions have visible, non-keyboard equivalents.
- Shortcut labels reflect operating system conventions.

## Responsive behavior

### Popup

Fixed compact layout. Long titles clamp to two lines; solution text never appears in full popup. Primary action remains above the fold.

### Side panel 360–480 px

- Single column.
- Evidence chips wrap to maximum two rows, then `+N`.
- Solution remains full width.
- Resource metadata stacks below title when narrower than 400 px.
- Footer actions may become a sticky bottom bar only during Ghost Replay.

### Full-page extension app

- Below 720 px: single column; metadata moves below solution.
- 720–1200 px: centered 720 px reading column.
- Above 1200 px: optional 240 px left navigation/recent list; main content remains maximum 760 px.
- No dense admin-style dashboard grid.

### Zoom

At 200% zoom, no horizontal scrolling is required for primary flows. Timeline technical strings may wrap; URLs use safe mid-string breaking.

## Accessibility

- WCAG 2.2 AA contrast and interaction targets.
- Minimum pointer target 32×32 px in dense surfaces; 44×44 where layout permits.
- Visible focus ring uses a two-tone outline distinguishable from memory violet state.
- State never relies on color alone: icon and text accompany violet/green/amber/red.
- Motion honors `prefers-reduced-motion`.
- Side-panel state changes announce concise live-region updates such as `Strong matching problem found`; no repeated announcements during resource loading.
- Generated summary, user solution, and evidence have semantic labels read by screen readers.
- Timeline is an ordered list, not a visual-only path.
- Dialogs trap focus correctly; close returns focus to invoker.
- Error text identifies the related field/control and is associated programmatically.
- Truncated visible text is available through focus/accessible name without requiring hover.

## Experience acceptance criteria

### Five-minute experience

- A first-time user can install, understand capture, run Demo Recall, and enable Trail without guidance.
- Demo content is unmistakably synthetic and deleted on completion.
- The user can state the product promise as remembering prior solutions, not browsing history.

### Capture and completion

- Automatic detection never opens UI or steals focus.
- Manual start/finish is reachable from the popup in one click.
- A developer can record a final solution in under 30 seconds at median.
- Closing or losing connectivity never discards typed solution text.

### Recall

- A strong match requires visible supporting evidence.
- Recall opens Ghost Replay in one invocation.
- Exact prior solution is visible without generative rewriting.
- Ghost Replay opens zero tabs until the user chooses a resource.
- Dismissing/not-related suppresses the pair for the current session.
- Closing Replay restores focus to the originating tab.

### AI restraint

- No chat input, streaming answer, first-person AI voice, diagnosis, or generated final solution exists.
- No uncalibrated percentage or invented time-saved estimate appears.
- AI failure never blocks saved solution access.
- Every proactive statement has evidence defined in the Evidence contract.

### Privacy and failure

- Pause and domain exclusion update locally before any server response.
- Offline mode clearly distinguishes local capture from synced memory.
- Error states state what remains safe/preserved.
- Delete actions clearly describe source, derived, and backup behavior.

## Features intentionally absent

- Chatbot, assistant avatar, prompt library, or answer generation.
- In-page overlays/content scripts in MVP.
- Code, terminal, repository, clipboard, or keystroke capture.
- Notifications that interrupt active debugging.
- Dashboard charts, streaks, productivity scores, or time-saved claims.
- Rich notes, documents, folders, graph view, collaboration, and social sharing.
- Auto-opening tabs or auto-applying old fixes.

These exclusions are part of the experience design, not missing polish.
