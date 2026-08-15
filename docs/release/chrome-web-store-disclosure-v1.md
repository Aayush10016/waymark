# Chrome Web Store Package and Disclosure v1

ENG-103 defines the Chrome Web Store release package and disclosure contract for Trail v1.1.

## Package contract

- Build command: `corepack pnpm --filter @trail/extension build`.
- Package command: `corepack pnpm --filter @trail/extension webstore:package`.
- Validate command: `corepack pnpm --filter @trail/extension webstore:validate`.
- Artifact: `apps/extension/dist/trail-extension-0.1.0.zip`.
- Checksum: `apps/extension/dist/trail-extension-0.1.0.sha256`.
- Source maps are not allowed in the package.
- Secrets, private keys, raw environment files, and local configuration are not allowed in the package.
- Remote code is not allowed; extension scripts must be packaged and served from the extension bundle.

## Permissions disclosure

| Permission | Purpose |
|---|---|
| `storage` | Store local consent, auth state, queue, drafts, and capture controls. |
| `tabs` | Read active tab title/URL metadata after explicit consent and local policy checks. |
| `webNavigation` | Capture top-level navigation events after consent. |
| `sidePanel` | Open Trail recall and replay surfaces. |
| `alarms` | Support local extension runtime scheduling already defined by the frozen extension architecture. |
| `http://localhost:8080/*` host permission | Allow the packaged local-development extension to call the local Trail backend API. Production packages must replace this with the exact HTTPS Trail API origin approved for release. |

The package must not declare content scripts, broad host permissions, history, identity, cookies, debugger, downloads, native messaging, or scripting permissions. Broad host access such as `<all_urls>`, `http://*/*`, `https://*/*`, or `*://*/*` is forbidden.

## Network disclosure

- Extension network traffic is limited to the configured Trail backend API.
- Local development network traffic is limited to `http://localhost:8080/*`; staging and production must use a narrow HTTPS Trail API host permission rather than arbitrary website access.
- The extension must not execute remote JavaScript.
- The extension must not contact AI providers directly.
- The extension must not connect directly to PostgreSQL or Render infrastructure.

## Rollout and rollback

- Initial rollout: private-beta or staged percentage approved by the release owner.
- Rollback: unpublish or halt rollout in Chrome Web Store and keep the previous approved package checksum.
- Backend rollback is handled independently by the Render image rollback procedure.

## Deferred

ENG-103 does not submit the package to Chrome Web Store, configure private-beta users, or publish a public release.
