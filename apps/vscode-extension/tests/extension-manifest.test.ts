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
    expect(manifest.version).toMatch(/^\d+\.\d+\.\d+$/u);
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
      (container) => container.id === "trail"
    );

    expect(activityBarContainer?.title).toBe("Waymark");
    expect(activityBarContainer?.icon).toBe("resources/waymark.svg");
    expect(existsSync(join(process.cwd(), activityBarContainer?.icon ?? ""))).toBe(true);
    expect(existsSync(join(process.cwd(), "resources/waymark.png"))).toBe(true);
  });

  it("maps Explain Selected Code to the real AI command and keeps Show Code Context local", async () => {
    const manifest = await readManifest();
    const commands = manifest.contributes.commands;

    expect(commandTitles(commands)).toContainEqual(["trail.explainSelectedCode", "Waymark: Explain Selected Code"]);
    expect(commandTitles(commands)).toContainEqual(["trail.askAboutSelectedCode", "Waymark: Ask About Selected Code"]);
    expect(commandTitles(commands)).toContainEqual(["trail.showCodeContext", "Waymark: Show Code Context"]);
    expect(commandTitles(commands)).not.toContainEqual(["trail.explainSelectedCodeWithAi", "Waymark: Explain Selected Code with AI"]);
  });

  it("registers user-facing commands exactly once", async () => {
    const manifest = await readManifest();
    const commandIds = manifest.contributes.commands.map((command) => command.command);

    expect(commandIds.filter((command) => command === "trail.explainSelectedCode")).toHaveLength(1);
    expect(commandIds.filter((command) => command === "trail.askAboutSelectedCode")).toHaveLength(1);
    expect(commandIds.filter((command) => command === "trail.showCodeContext")).toHaveLength(1);
    expect(commandIds.filter((command) => command === "trail.checkSelectedCode")).toHaveLength(1);
    expect(commandIds.filter((command) => command === "trail.importArchitectureDocument")).toHaveLength(1);
    expect(commandIds.filter((command) => command === "trail.deleteRule")).toHaveLength(1);
  });

  it("contributes selected-code editor context menu actions under Waymark", async () => {
    const manifest = await readManifest();

    expect(manifest.contributes.submenus).toContainEqual({
      id: "trail.editorContext",
      label: "Waymark"
    });
    expect(manifest.contributes.menus["editor/context"]).toContainEqual({
      submenu: "trail.editorContext",
      when: "editorHasSelection",
      group: "navigation@10"
    });
    expect(manifest.contributes.menus["trail.editorContext"]).toEqual([
      expect.objectContaining({ command: "trail.explainSelectedCode", when: "editorHasSelection" }),
      expect.objectContaining({ command: "trail.askAboutSelectedCode", when: "editorHasSelection" }),
      expect.objectContaining({ command: "trail.checkSelectedCode", when: "editorHasSelection" }),
      expect.objectContaining({ command: "trail.showCodeContext", when: "editorHasSelection" })
    ]);
  });

  it("activates renamed commands and removes stale AI-specific command IDs", async () => {
    const manifest = await readManifest();

    expect(manifest.activationEvents).toEqual(expect.arrayContaining([
      "onCommand:trail.explainSelectedCode",
      "onCommand:trail.askAboutSelectedCode",
      "onCommand:trail.showAiConfiguration"
    ]));
    expect(manifest.activationEvents).not.toContain("onCommand:trail.explainSelectedCodeWithAi");
    expect(manifest.activationEvents).not.toContain("onCommand:trail.askAboutSelectedCodeWithAi");
  });

  it("contributes sidebar rule actions without expanding the editor context menu", async () => {
    const manifest = await readManifest();

    expect(commandTitles(manifest.contributes.commands)).toContainEqual([
      "trail.importArchitectureDocument",
      "Waymark: Import Architecture Document"
    ]);
    expect(commandTitles(manifest.contributes.commands)).toContainEqual(["trail.deleteRule", "Waymark: Delete Rule"]);
    expect(manifest.contributes.menus["view/item/context"]).toContainEqual({
      command: "trail.deleteRule",
      when: "view == trail.projectMemory && viewItem == trailRule",
      group: "inline@2"
    });
    expect(manifest.contributes.menus["trail.editorContext"]).toHaveLength(4);
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
    readonly menus: Record<string, readonly Record<string, string>[]>;
  };
}

async function readManifest(): Promise<ExtensionManifest> {
  return JSON.parse(await readFile(join(process.cwd(), "package.json"), "utf8")) as ExtensionManifest;
}

function commandTitles(commands: ExtensionManifest["contributes"]["commands"]): readonly (readonly [string, string])[] {
  return commands.map((command) => [command.command, command.title] as const);
}
