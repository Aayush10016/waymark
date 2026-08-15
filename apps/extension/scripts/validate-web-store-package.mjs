import { readdirSync, readFileSync } from "node:fs";
import { extname, join } from "node:path";

const distRoot = "dist";
const forbiddenManifestFields = ["content_scripts", "externally_connectable", "incognito"];
const forbiddenPermissions = ["cookies", "debugger", "downloads", "history", "identity", "nativeMessaging", "scripting"];
const expectedPermissions = ["alarms", "sidePanel", "storage", "tabs", "webNavigation"];
const expectedHostPermissions = ["http://localhost:8080/*"];
const forbiddenHostPermissions = ["<all_urls>", "http://*/*", "https://*/*", "*://*/*"];
const textExtensions = new Set([".html", ".js", ".json", ".sha256", ".txt"]);

function fail(message) {
  throw new Error(`Invalid Web Store package: ${message}`);
}

function walk(directory) {
  const files = [];
  for (const entry of readdirSync(directory, { withFileTypes: true })) {
    const path = join(directory, entry.name);
    if (entry.isDirectory()) {
      files.push(...walk(path));
    } else if (entry.isFile()) {
      files.push(path);
    }
  }
  return files;
}

const manifest = JSON.parse(readFileSync(join(distRoot, "manifest.json"), "utf8"));
for (const field of forbiddenManifestFields) {
  if (Object.hasOwn(manifest, field)) {
    fail(`${field} is not allowed`);
  }
}

const permissions = manifest.permissions ?? [];
if (JSON.stringify([...permissions].sort()) !== JSON.stringify([...expectedPermissions].sort())) {
  fail("manifest permissions do not match the approved disclosure");
}

for (const permission of forbiddenPermissions) {
  if (permissions.includes(permission)) {
    fail(`${permission} permission is not allowed`);
  }
}

const hostPermissions = manifest.host_permissions ?? [];
if (JSON.stringify([...hostPermissions].sort()) !== JSON.stringify([...expectedHostPermissions].sort())) {
  fail("manifest host permissions do not match the approved disclosure");
}

for (const hostPermission of hostPermissions) {
  if (forbiddenHostPermissions.includes(hostPermission)) {
    fail(`${hostPermission} host permission is not allowed`);
  }
}

for (const file of walk(distRoot)) {
  if (file.endsWith(".map")) {
    fail(`source map found at ${file}`);
  }
  if (file.endsWith(".js")) {
    const content = readFileSync(file, "utf8");
    if (content.includes("eval(") || content.includes("new Function(")) {
      fail(`remote-code-like dynamic execution found at ${file}`);
    }
  }
  if (file.endsWith(".env")) {
    fail(`secret-like package file found at ${file}`);
  }
  if (textExtensions.has(extname(file))) {
    const content = readFileSync(file, "utf8");
    if (content.includes("PRIVATE KEY") || /sk-[A-Za-z0-9]{20,}/.test(content)) {
      fail(`secret-like package content found at ${file}`);
    }
  }
}

console.log("Chrome Web Store package validation passed.");
