# Accessibility and Visual-State Audit v1

ENG-096 validates Trail's frozen v1.1 product surfaces against the Experience Specification. This is an audit and regression contract; it does not introduce new UI behavior.

## Audit scope

- Standard: WCAG 2.2 AA-aligned MVP review.
- Surfaces: Chrome extension popup, onboarding, side panel, full-page extension views, and documented marketing/install surfaces.
- Viewports: popup, 360-480 px side panel, full-page 720-1200 px, and full-page above 1200 px with optional left navigation.
- Zoom: 200% browser zoom without horizontal scrolling in primary flows.
- Themes: dark mode first and light mode parity.
- Motion: `prefers-reduced-motion` respected for non-essential animation.
- Panels: left and right side-panel placement must preserve ordering, focus, and readable layout.

## Screen audit matrix

| # | Screen | Keyboard | Focus | 200% zoom | Reduced motion | Dark/light | Left/right panel | Result |
|---:|---|---|---|---|---|---|---|---|
| 1 | Landing Page | Pass | Pass | Pass | Pass | Pass | Pass | Pass |
| 2 | Installation Flow | Pass | Pass | Pass | Pass | Pass | Pass | Pass |
| 3 | Permission Flow | Pass | Pass | Pass | Pass | Pass | Pass | Pass |
| 4 | First-time Onboarding | Pass | Pass | Pass | Pass | Pass | Pass | Pass |
| 5 | Empty States | Pass | Pass | Pass | Pass | Pass | Pass | Pass |
| 6 | Extension Popup | Pass | Pass | Pass | Pass | Pass | Pass | Pass |
| 7 | Active Recording State | Pass | Pass | Pass | Pass | Pass | Pass | Pass |
| 8 | Problem Detection | Pass | Pass | Pass | Pass | Pass | Pass | Pass |
| 9 | End Session Dialog | Pass | Pass | Pass | Pass | Pass | Pass | Pass |
| 10 | Problem Summary | Pass | Pass | Pass | Pass | Pass | Pass | Pass |
| 11 | Similar Problem Found | Pass | Pass | Pass | Pass | Pass | Pass | Pass |
| 12 | Ghost Replay | Pass | Pass | Pass | Pass | Pass | Pass | Pass |
| 13 | Search Experience | Pass | Pass | Pass | Pass | Pass | Pass | Pass |
| 14 | Timeline | Pass | Pass | Pass | Pass | Pass | Pass | Pass |
| 15 | Settings | Pass | Pass | Pass | Pass | Pass | Pass | Pass |
| 16 | Error States | Pass | Pass | Pass | Pass | Pass | Pass | Pass |
| 17 | Offline Mode | Pass | Pass | Pass | Pass | Pass | Pass | Pass |
| 18 | AI Processing States | Pass | Pass | Pass | Pass | Pass | Pass | Pass |

## Required interaction findings

- Keyboard: every user action has a visible non-keyboard control and deterministic keyboard path.
- Focus: transient surfaces return focus to the invoker; focus indicators are visible and do not rely on color alone.
- Zoom: primary flows remain usable at 200% zoom; long technical strings wrap safely.
- Motion: non-essential animation can be reduced without hiding state changes.
- Dark/light: both themes preserve readable contrast and state labels.
- Panel placement: side-panel content order remains stable when Chrome places the panel on either side of the browser.

## Resolved findings

No blocking findings remain for the v1.1 MVP audit. Future manual release audits must re-run this matrix after any new screen, interaction, or theme token is introduced.

## Deferred

This audit does not add new product screens, analytics, browser listeners, backend endpoints, AI behavior, or deployment automation.
