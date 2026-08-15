import { readFileSync } from "node:fs";

const manifest = JSON.parse(readFileSync("manifest/manifest.json", "utf8"));

const expectedPermissions = ["alarms", "sidePanel", "storage", "tabs", "webNavigation"];
const expectedHostPermissions = ["http://localhost:8080/*"];
const forbiddenFields = ["content_scripts", "externally_connectable", "incognito"];
const forbiddenPermissions = [
  "cookies",
  "debugger",
  "downloads",
  "history",
  "identity",
  "nativeMessaging",
  "scripting"
];
const forbiddenHostPermissions = ["<all_urls>", "http://*/*", "https://*/*", "*://*/*"];

function fail(message) {
  throw new Error(`Invalid MV3 manifest: ${message}`);
}

if (manifest.manifest_version !== 3) {
  fail("manifest_version must be 3");
}

if (manifest.minimum_chrome_version !== "149") {
  fail("minimum_chrome_version must support current and previous stable Chrome only");
}

if (manifest.background?.service_worker !== "background.js" || manifest.background?.type !== "module") {
  fail("background must use a module service worker");
}

if (manifest.action?.default_popup !== "popup.html") {
  fail("action.default_popup must point to popup.html");
}

if (manifest.side_panel?.default_path !== "sidepanel.html") {
  fail("side_panel.default_path must point to sidepanel.html");
}

for (const field of forbiddenFields) {
  if (Object.hasOwn(manifest, field)) {
    fail(`${field} is not allowed in MVP`);
  }
}

const permissions = manifest.permissions ?? [];
if (JSON.stringify([...permissions].sort()) !== JSON.stringify([...expectedPermissions].sort())) {
  fail(`permissions must be exactly ${expectedPermissions.join(", ")}`);
}

for (const permission of forbiddenPermissions) {
  if (permissions.includes(permission)) {
    fail(`${permission} permission is not allowed in MVP`);
  }
}

const hostPermissions = manifest.host_permissions ?? [];
if (JSON.stringify([...hostPermissions].sort()) !== JSON.stringify([...expectedHostPermissions].sort())) {
  fail(`host_permissions must be exactly ${expectedHostPermissions.join(", ")}`);
}

for (const hostPermission of hostPermissions) {
  if (forbiddenHostPermissions.includes(hostPermission)) {
    fail(`${hostPermission} host permission is not allowed in MVP`);
  }
}
