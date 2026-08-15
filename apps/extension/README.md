# Trail Chrome Extension

Purpose: Chrome Manifest V3 extension shell for Trail surfaces.

Owner: Extension Owner.

Local verification:

```powershell
pnpm --filter @trail/extension install
pnpm --filter @trail/extension validate:manifest
pnpm --filter @trail/extension build
```

Boundaries:

- No content scripts in MVP.
- No `identity`, host, history, cookies, downloads, clipboard, debugger, scripting, externally connectable, native messaging, or incognito permissions.
- Background service worker exists as a bootstrap entry only; no Chrome event handling is implemented in ENG-008.

