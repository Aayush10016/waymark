# Chrome MV3 Validation

Status: ENG-008 validation record.

## Supported browser baseline

- Current stable Chrome at validation: `150.0.7871.47`.
- Supported desktop Chrome lines for Trail v1.1 foundation: current and previous stable, `150` and `149`.
- Extension manifest `minimum_chrome_version`: `149`.

## Approved permissions

The MVP manifest may request exactly:

- `storage`
- `alarms`
- `tabs`
- `webNavigation`
- `sidePanel`

## Explicitly absent permissions and fields

The foundation manifest must not include:

- `identity`
- `content_scripts`
- `host_permissions`
- `history`
- `cookies`
- `downloads`
- `clipboardRead`
- `clipboardWrite`
- `debugger`
- `scripting`
- `externally_connectable`
- `nativeMessaging`
- `incognito`

## Expected user-facing permission warning

Chrome combines the approved `tabs` and `webNavigation` permissions into a browsing-activity warning. The expected install warning is:

> Read your browsing history

The extension must not request host access, page content access, identity access, or content-script injection in the MVP foundation.

## Lifecycle validation

- Background is a Manifest V3 module service worker.
- Service worker code contains no event listeners in ENG-008.
- Future correctness state must persist durably because MV3 service workers may stop between events.
- Privileged Chrome actions must be routed through the service worker when feature issues add them.
- Side panel opens only through user gestures or scoped future commands; no automatic focus theft is authorized by ENG-008.

## Automated checks

`pnpm --filter @trail/extension validate:manifest` enforces:

- Manifest V3.
- `minimum_chrome_version` is `149`.
- Required popup, side-panel, and service-worker paths.
- Exact permission allowlist.
- Absence of forbidden fields and permissions.

