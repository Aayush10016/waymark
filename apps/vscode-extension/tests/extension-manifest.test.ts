import { existsSync } from "node:fs";
import { readFile } from "node:fs/promises";
import { join } from "node:path";

import { describe, expect, it } from "vitest";

describe("VS Code extension manifest", () => {
  it("contains valid local VSIX packaging metadata", async () => {
    const manifest = await readManifest();

    expect(manifest.name).toBe("waymark");
    expect(manifest.displayName).toBe("Waymark");
    expect(manifest.publisher).toBe("Waymark-Dev");
    expect(manifest.private).not.toBe(true);
    expect(manifest.description).toBe("Keep AI-assisted development aligned with your project's rules and decisions.");
    expect(manifest.categories).toEqual(["Linters", "Other"]);
    expect(manifest.keywords).toEqual(expect.arrayContaining([
      "ai",
      "agent",
      "code review",
      "project rules",
      "architecture decisions",
      "guardian",
      "governance",
      "nvidia"
    ]));
    expect(manifest.version).toBe("0.1.2");
    expect(manifest.engines.vscode).toBe("^1.125.0");
    expect(manifest.main).toBe("./dist/extension.js");
    expect(manifest.icon).toBe("resources/waymark.png");
    expect(manifest.license).toBe("MIT");
    expect(manifest.repository).toEqual({
      type: "git",
      url: "https://github.com/Aayush10016/waymark.git",
      directory: "apps/vscode-extension"
    });
    expect(manifest.bugs).toEqual({
      url: "https://github.com/Aayush10016/waymark/issues"
    });
    expect(manifest.homepage).toBe("https://github.com/Aayush10016/waymark#readme");
    expect(existsSync(join(process.cwd(), "LICENSE"))).toBe(true);
    expect(manifest.scripts["package:vsix"]).toContain("vsce package");
    expect(manifest.devDependencies["@vscode/vsce"]).toBeDefined();
  });

  it("references extension resources that are packageable from the extension root", async () => {
    const manifest = await readManifest();
    const activityBarContainer = manifest.contributes.viewsContainers.activitybar.find(
      (container) => container.id === "waymark"
    );

    expect(activityBarContainer?.title).toBe("Waymark");
    expect(activityBarContainer?.icon).toBe("resources/waymark.svg");
    expect(existsSync(join(process.cwd(), activityBarContainer?.icon ?? ""))).toBe(true);
    expect(existsSync(join(process.cwd(), "resources/waymark.png"))).toBe(true);
  });

  it("uses a packageable VS Code-compatible SVG for the Activity Bar icon", async () => {
    const manifest = await readManifest();
    const activityBarContainer = manifest.contributes.viewsContainers.activitybar.find(
      (container) => container.id === "waymark"
    );
    const svgPath = activityBarContainer?.icon ?? "";
    const svg = await readFile(join(process.cwd(), svgPath), "utf8");

    expect(svgPath).toBe("resources/waymark.svg");
    expect(existsSync(join(process.cwd(), svgPath))).toBe(true);
    expect(svg).toContain("<svg");
    expect(svg).toMatch(/\bviewBox=/u);
    expect(svg).toMatch(/\bviewBox=["']0 0 24 24["']/u);
    expect(svg).toMatch(/<path\b[^>]+\bd=["'][^"']+["']/iu);
    expect(svg).not.toMatch(/<image\b/iu);
    expect(svg).not.toMatch(/\bhref\s*=\s*["'](?:https?:\/\/|data:)/iu);
    expect(svg).not.toMatch(/\bxlink:href\s*=\s*["'](?:https?:\/\/|data:)/iu);
    expect(svg).not.toMatch(/<script\b/iu);
    expect(svg).not.toMatch(/<style\b/iu);
    expect(svg).not.toMatch(/<foreignObject\b/iu);
    expect(svg).not.toMatch(/<(?:linearGradient|radialGradient|filter|mask|clipPath|animate)\b/iu);
  });

  it("uses Waymark-specific view and menu contribution IDs", async () => {
    const manifest = await readManifest();

    expect(manifest.contributes.viewsContainers.activitybar).toContainEqual(expect.objectContaining({
      id: "waymark",
      title: "Waymark"
    }));
    expect(Object.keys(manifest.contributes.views)).toContain("waymark");
    expect(manifest.contributes.views.waymark).toContainEqual(expect.objectContaining({
      id: "waymark.projectMemory",
      name: "Project Memory"
    }));
    expect(manifest.contributes.submenus).toContainEqual({
      id: "waymark.editorContext",
      label: "Waymark"
    });
  });

  it("maps Explain Selected Code to the real AI command and keeps Show Code Context local", async () => {
    const manifest = await readManifest();
    const commands = manifest.contributes.commands;

    expect(commandTitles(commands)).toContainEqual(["waymark.explainSelectedCode", "Waymark: Explain Selected Code"]);
    expect(commandTitles(commands)).toContainEqual(["waymark.askAboutSelectedCode", "Waymark: Ask About Selected Code"]);
    expect(commandTitles(commands)).toContainEqual(["waymark.showCodeContext", "Waymark: Show Code Context"]);
    expect(commandTitles(commands)).not.toContainEqual(["waymark.explainSelectedCodeWithAi", "Waymark: Explain Selected Code with AI"]);
  });

  it("registers user-facing commands exactly once", async () => {
    const manifest = await readManifest();
    const commandIds = manifest.contributes.commands.map((command) => command.command);

    expect(commandIds.filter((command) => command === "waymark.explainSelectedCode")).toHaveLength(1);
    expect(commandIds.filter((command) => command === "waymark.migrateLegacyProjectMemory")).toHaveLength(1);
    expect(commandIds.filter((command) => command === "waymark.askAboutSelectedCode")).toHaveLength(1);
    expect(commandIds.filter((command) => command === "waymark.showCodeContext")).toHaveLength(1);
    expect(commandIds.filter((command) => command === "waymark.checkSelectedCode")).toHaveLength(1);
    expect(commandIds.filter((command) => command === "waymark.importArchitectureDocument")).toHaveLength(1);
    expect(commandIds.filter((command) => command === "waymark.deleteRule")).toHaveLength(1);
  });

  it("does not contribute stale Trail command, view, or submenu IDs", async () => {
    const manifest = await readManifest();
    const commandIds = manifest.contributes.commands.map((command) => command.command);
    const activationEvents = manifest.activationEvents;
    const menuKeys = Object.keys(manifest.contributes.menus);
    const viewKeys = Object.keys(manifest.contributes.views);
    const viewIds = Object.values(manifest.contributes.views).flat().map((view) => view.id);
    const submenuIds = manifest.contributes.submenus.map((submenu) => submenu.id);
    const serializedContributions = JSON.stringify({
      activationEvents,
      contributes: manifest.contributes
    });

    expect(commandIds.filter((command) => command.startsWith("trail."))).toEqual([]);
    expect(activationEvents.filter((event) => event.startsWith("onCommand:trail."))).toEqual([]);
    expect(activationEvents).not.toContain("onView:trail.projectMemory");
    expect(viewKeys).not.toContain("trail");
    expect(viewIds).not.toContain("trail.projectMemory");
    expect(menuKeys).not.toContain("trail.editorContext");
    expect(submenuIds).not.toContain("trail.editorContext");
    expect(serializedContributions).not.toContain("trail.");
    expect(serializedContributions).not.toContain("trail.projectMemory");
    expect(serializedContributions).not.toContain("trail.editorContext");
  });

  it("contributes selected-code editor context menu actions under Waymark", async () => {
    const manifest = await readManifest();

    expect(manifest.contributes.submenus).toContainEqual({
      id: "waymark.editorContext",
      label: "Waymark"
    });
    expect(manifest.contributes.menus["editor/context"]).toContainEqual({
      submenu: "waymark.editorContext",
      when: "editorHasSelection",
      group: "navigation@10"
    });
    expect(manifest.contributes.menus["waymark.editorContext"]).toEqual([
      expect.objectContaining({ command: "waymark.explainSelectedCode", when: "editorHasSelection" }),
      expect.objectContaining({ command: "waymark.askAboutSelectedCode", when: "editorHasSelection" }),
      expect.objectContaining({ command: "waymark.checkSelectedCode", when: "editorHasSelection" }),
      expect.objectContaining({ command: "waymark.showCodeContext", when: "editorHasSelection" })
    ]);
  });

  it("activates renamed commands and removes stale AI-specific command IDs", async () => {
    const manifest = await readManifest();

    expect(manifest.activationEvents).toEqual(expect.arrayContaining([
      "onCommand:waymark.explainSelectedCode",
      "onCommand:waymark.askAboutSelectedCode",
      "onCommand:waymark.showAiConfiguration"
    ]));
    expect(manifest.activationEvents).not.toContain("onCommand:waymark.explainSelectedCodeWithAi");
    expect(manifest.activationEvents).not.toContain("onCommand:waymark.askAboutSelectedCodeWithAi");
  });

  it("contributes sidebar rule actions without expanding the editor context menu", async () => {
    const manifest = await readManifest();

    expect(commandTitles(manifest.contributes.commands)).toContainEqual([
      "waymark.importArchitectureDocument",
      "Waymark: Import Architecture Document"
    ]);
    expect(commandTitles(manifest.contributes.commands)).toContainEqual(["waymark.deleteRule", "Waymark: Delete Rule"]);
    expect(manifest.contributes.menus["view/item/context"]).toContainEqual({
      command: "waymark.deleteRule",
      when: "view == waymark.projectMemory && viewItem == waymarkRule",
      group: "inline@2"
    });
    expect(manifest.contributes.menus["waymark.editorContext"]).toHaveLength(4);
  });
});

interface ExtensionManifest {
  readonly name: string;
  readonly displayName: string;
  readonly publisher: string;
  readonly private?: boolean;
  readonly description: string;
  readonly categories: readonly string[];
  readonly keywords: readonly string[];
  readonly version: string;
  readonly engines: {
    readonly vscode: string;
  };
  readonly main: string;
  readonly icon: string;
  readonly license: string;
  readonly repository: {
    readonly type: string;
    readonly url: string;
    readonly directory: string;
  };
  readonly bugs: {
    readonly url: string;
  };
  readonly homepage: string;
  readonly scripts: Record<string, string>;
  readonly devDependencies: Record<string, string>;
  readonly activationEvents: readonly string[];
  readonly contributes: {
    readonly commands: ReadonlyArray<{
      readonly command: string;
      readonly title: string;
    }>;
    readonly submenus: ReadonlyArray<{
      readonly id: string;
      readonly label: string;
    }>;
    readonly viewsContainers: {
      readonly activitybar: ReadonlyArray<{
        readonly id: string;
        readonly title: string;
        readonly icon: string;
      }>;
    };
    readonly views: Record<string, ReadonlyArray<{
      readonly id: string;
      readonly name: string;
    }>>;
    readonly menus: Record<string, readonly Record<string, string>[]>;
  };
}

async function readManifest(): Promise<ExtensionManifest> {
  return JSON.parse(await readFile(join(process.cwd(), "package.json"), "utf8")) as ExtensionManifest;
}

function commandTitles(commands: ExtensionManifest["contributes"]["commands"]): readonly (readonly [string, string])[] {
  return commands.map((command) => [command.command, command.title] as const);
}
