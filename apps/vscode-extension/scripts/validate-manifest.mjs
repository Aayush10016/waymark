import { existsSync, readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { join } from "node:path";

const packageJsonPath = new URL("../package.json", import.meta.url);
const extensionRoot = fileURLToPath(new URL("..", import.meta.url));
const manifest = JSON.parse(readFileSync(packageJsonPath, "utf8"));

if (manifest.name !== "waymark") {
  throw new Error("Unexpected extension package name.");
}

if (manifest.displayName !== "Waymark") {
  throw new Error("Missing Waymark display name.");
}

if (manifest.publisher !== "Waymark-Dev") {
  throw new Error("Missing Waymark Marketplace publisher metadata.");
}

if (manifest.private === true) {
  throw new Error("Waymark Marketplace package must not be marked private.");
}

if (manifest.description !== "Keep AI-assisted development aligned with your project's rules and decisions.") {
  throw new Error("Waymark Marketplace description is not release-ready.");
}

if (!Array.isArray(manifest.categories) || !manifest.categories.includes("Linters") || !manifest.categories.includes("Other")) {
  throw new Error("Waymark Marketplace categories are incomplete.");
}

if (!Array.isArray(manifest.keywords) || manifest.keywords.length < 5) {
  throw new Error("Waymark Marketplace keywords are incomplete.");
}

if (manifest.main !== "./dist/extension.js") {
  throw new Error("Waymark extension main entry must point to ./dist/extension.js.");
}

if (manifest.engines?.vscode !== "^1.125.0") {
  throw new Error("Waymark VS Code engine must match the pinned @types/vscode API target.");
}

if (manifest.icon !== "resources/waymark.png") {
  throw new Error("Waymark Marketplace package icon must be a PNG asset.");
}

if (manifest.license !== "MIT") {
  throw new Error("Waymark Marketplace license metadata must reference MIT.");
}

if (manifest.repository?.type !== "git" || manifest.repository.url !== "https://github.com/Aayush10016/waymark.git") {
  throw new Error("Waymark Marketplace repository metadata is missing.");
}

if (manifest.repository.directory !== "apps/vscode-extension") {
  throw new Error("Waymark Marketplace repository metadata must point to the extension directory.");
}

if (manifest.bugs?.url !== "https://github.com/Aayush10016/waymark/issues") {
  throw new Error("Waymark Marketplace bugs URL is missing.");
}

if (manifest.homepage !== "https://github.com/Aayush10016/waymark#readme") {
  throw new Error("Waymark Marketplace homepage URL is missing.");
}

if (!existsSync(join(extensionRoot, "LICENSE"))) {
  throw new Error("Waymark Marketplace package license file is missing.");
}

const packageIconPath = join(extensionRoot, manifest.icon);
if (!existsSync(packageIconPath)) {
  throw new Error("Waymark Marketplace package icon is missing.");
}

const packageIcon = readFileSync(packageIconPath);
if (!isPng(packageIcon) || pngDimensions(packageIcon).width !== 128 || pngDimensions(packageIcon).height !== 128) {
  throw new Error("Waymark Marketplace package icon must be a 128x128 PNG.");
}

if (!manifest.scripts?.["package:vsix"]?.includes("vsce package")) {
  throw new Error("Missing local VSIX package script.");
}

if (manifest.devDependencies?.["@vscode/vsce"] === undefined) {
  throw new Error("Missing local VSCE packaging dependency.");
}

const requiredCommands = new Set([
  "trail.openProjectMemory",
  "trail.initializeProject",
  "trail.selectWorkspaceFolder",
  "trail.createConstitution",
  "trail.openConstitution",
  "trail.addRule",
  "trail.deleteRule",
  "trail.addDecision",
  "trail.importArchitectureDocument",
  "trail.showFutureFeature",
  "trail.refreshGitContext",
  "trail.openChangedFile",
  "trail.viewFileDiff",
  "trail.showCommitDetails",
  "trail.searchProjectMemory",
  "trail.refreshProjectMemory",
  "trail.showMemoryResultDetails",
  "trail.explainSelectedCode",
  "trail.showCodeContext",
  "trail.checkSelectedCode",
  "trail.checkCurrentChanges",
  "trail.showGuardianFinding",
  "trail.askAboutSelectedCode",
  "trail.showAiConfiguration"
]);

const commands = new Set(
  (manifest.contributes?.commands ?? []).map((command) => command.command)
);

for (const staleCommand of [
  "trail.explainSelectedCodeWithAi",
  "trail.askAboutSelectedCodeWithAi"
]) {
  if (commands.has(staleCommand)) {
    throw new Error(`Stale command contribution remains registered: ${staleCommand}`);
  }
}

for (const requiredCommand of requiredCommands) {
  if (!commands.has(requiredCommand)) {
    throw new Error(`Missing command contribution: ${requiredCommand}`);
  }
}

const activationEvents = new Set(manifest.activationEvents ?? []);
for (const requiredCommand of requiredCommands) {
  if (!activationEvents.has(`onCommand:${requiredCommand}`)) {
    throw new Error(`Missing activation event for command: ${requiredCommand}`);
  }
}

const containers = manifest.contributes?.viewsContainers?.activitybar ?? [];
if (!containers.some((container) => container.id === "trail")) {
  throw new Error("Missing Waymark Activity Bar container.");
}
if (!containers.some((container) => container.id === "trail" && container.title === "Waymark")) {
  throw new Error("Waymark Activity Bar container must use Waymark branding.");
}

const views = manifest.contributes?.views?.trail ?? [];
if (!views.some((view) => view.id === "trail.projectMemory")) {
  throw new Error("Missing Waymark Project Memory view.");
}

const editorContextMenus = manifest.contributes?.menus?.["editor/context"] ?? [];
if (!editorContextMenus.some((menu) => menu.submenu === "trail.editorContext" && menu.when === "editorHasSelection")) {
  throw new Error("Missing selected-code Waymark editor context submenu.");
}

const trailEditorMenuCommands = new Set(
  (manifest.contributes?.menus?.["trail.editorContext"] ?? []).map((menu) => menu.command)
);
for (const requiredEditorCommand of [
  "trail.explainSelectedCode",
  "trail.askAboutSelectedCode",
  "trail.checkSelectedCode",
  "trail.showCodeContext"
]) {
  if (!trailEditorMenuCommands.has(requiredEditorCommand)) {
    throw new Error(`Missing Waymark editor context command: ${requiredEditorCommand}`);
  }
}

const mainPath = join(extensionRoot, manifest.main);
if (!existsSync(mainPath)) {
  throw new Error(`Compiled extension entry does not exist: ${manifest.main}`);
}

const iconPath = containers.find((container) => container.id === "trail")?.icon;
if (typeof iconPath !== "string" || !existsSync(join(extensionRoot, iconPath))) {
  throw new Error("Waymark Activity Bar icon is missing.");
}

function isPng(content) {
  return content.length > 24
    && content[0] === 0x89
    && content[1] === 0x50
    && content[2] === 0x4e
    && content[3] === 0x47
    && content[4] === 0x0d
    && content[5] === 0x0a
    && content[6] === 0x1a
    && content[7] === 0x0a;
}

function pngDimensions(content) {
  return {
    width: content.readUInt32BE(16),
    height: content.readUInt32BE(20)
  };
}
