import { access, mkdir, mkdtemp, readFile, rm, writeFile } from "node:fs/promises";
import { join } from "node:path";
import { tmpdir } from "node:os";

import { afterEach, describe, expect, it } from "vitest";

import { ProjectMarkerStore } from "../src/workspace/project-marker";
import {
  PROJECT_MEMORY_WATCH_PATTERN,
  canonicalProjectMemoryPaths,
  legacyProjectMemoryPaths,
  migrateLegacyProjectMemoryToCanonical,
  resolveProjectMemoryPaths
} from "../src/workspace/project-memory-paths";

const tempRoots: string[] = [];

describe("ProjectMarkerStore", () => {
  afterEach(async () => {
    await Promise.all(tempRoots.map((path) => rm(path, { recursive: true, force: true })));
    tempRoots.length = 0;
  });

  it("reports a missing project marker for a new workspace", async () => {
    const workspace = await createWorkspace("example");
    const store = new ProjectMarkerStore(() => "project-1");

    await expect(store.read(workspace)).resolves.toMatchObject({
      status: "missing"
    });
  });

  it("reads an existing valid project marker", async () => {
    const workspace = await createWorkspace("example");
    await writeFile(
      join(workspace.fsPath, ".waymark", "project.json"),
      JSON.stringify({ schemaVersion: 1, projectId: "existing-id", name: "example" }),
      "utf8"
    );
    const store = new ProjectMarkerStore(() => "project-1");

    await expect(store.read(workspace)).resolves.toMatchObject({
      status: "valid",
      project: {
        schemaVersion: 1,
        projectId: "existing-id",
        name: "example"
      }
    });
  });

  it("initializes a workspace by creating .waymark/project.json", async () => {
    const workspace = await createWorkspace("example");
    const store = new ProjectMarkerStore(() => "project-1");

    const project = await store.initialize(workspace);

    expect(project).toEqual({
      schemaVersion: 1,
      projectId: "project-1",
      name: "example"
    });
    await expect(readFile(join(workspace.fsPath, ".waymark", "project.json"), "utf8")).resolves.toContain(
      '"projectId": "project-1"'
    );
    await expect(pathExists(join(workspace.fsPath, ".trail"))).resolves.toBe(false);
  });

  it("keeps the generated project id stable after initialization", async () => {
    const workspace = await createWorkspace("example");
    const store = new ProjectMarkerStore(() => "project-1");

    await store.initialize(workspace);
    const result = await store.read(workspace);

    expect(result).toMatchObject({
      status: "valid",
      project: {
        projectId: "project-1"
      }
    });
  });

  it("does not overwrite an existing valid project marker", async () => {
    const workspace = await createWorkspace("example");
    const markerPath = join(workspace.fsPath, ".waymark", "project.json");
    const existing = {
      schemaVersion: 1,
      projectId: "existing-id",
      name: "Existing"
    };
    await writeFile(markerPath, JSON.stringify(existing, null, 2), "utf8");
    const store = new ProjectMarkerStore(() => "new-id");

    const project = await store.initialize(workspace);
    const saved = JSON.parse(await readFile(markerPath, "utf8")) as unknown;

    expect(project).toEqual(existing);
    expect(saved).toEqual(existing);
  });

  it("handles malformed project markers safely", async () => {
    const workspace = await createWorkspace("example");
    await writeFile(join(workspace.fsPath, ".waymark", "project.json"), "{", "utf8");
    const store = new ProjectMarkerStore(() => "project-1");

    await expect(store.read(workspace)).resolves.toMatchObject({
      status: "malformed"
    });
    await expect(store.initialize(workspace)).rejects.toThrow("already exists but is not valid JSON");
  });

  it("reads an existing legacy .trail project marker", async () => {
    const workspace = await createWorkspace("example", "legacy");
    await writeFile(
      join(workspace.fsPath, ".trail", "project.json"),
      JSON.stringify({ schemaVersion: 1, projectId: "legacy-id", name: "example" }),
      "utf8"
    );
    const store = new ProjectMarkerStore(() => "project-1");

    await expect(store.read(workspace)).resolves.toMatchObject({
      status: "valid",
      markerPath: join(workspace.fsPath, ".trail", "project.json"),
      project: {
        projectId: "legacy-id"
      }
    });
  });

  it("uses .waymark as authoritative when canonical and legacy project markers both exist", async () => {
    const workspace = await createWorkspace("example", "none");
    await mkdir(join(workspace.fsPath, ".trail"), { recursive: true });
    await mkdir(join(workspace.fsPath, ".waymark"), { recursive: true });
    await writeFile(
      join(workspace.fsPath, ".trail", "project.json"),
      JSON.stringify({ schemaVersion: 1, projectId: "legacy-id", name: "Legacy" }),
      "utf8"
    );
    await writeFile(
      join(workspace.fsPath, ".waymark", "project.json"),
      JSON.stringify({ schemaVersion: 1, projectId: "canonical-id", name: "Canonical" }),
      "utf8"
    );

    await expect(new ProjectMarkerStore().read(workspace)).resolves.toMatchObject({
      status: "valid",
      markerPath: join(workspace.fsPath, ".waymark", "project.json"),
      project: {
        projectId: "canonical-id"
      }
    });
  });
});

describe("project memory path resolution and migration", () => {
  afterEach(async () => {
    await Promise.all(tempRoots.map((path) => rm(path, { recursive: true, force: true })));
    tempRoots.length = 0;
  });

  it("detects canonical .waymark project memory", async () => {
    const workspace = await createWorkspace("example", "canonical");

    expect(resolveProjectMemoryPaths(workspace)).toMatchObject({
      directoryName: ".waymark",
      directoryFormat: "canonical",
      projectMemoryDirectoryPath: join(workspace.fsPath, ".waymark")
    });
  });

  it("detects legacy .trail project memory", async () => {
    const workspace = await createWorkspace("example", "legacy");

    expect(resolveProjectMemoryPaths(workspace)).toMatchObject({
      directoryName: ".trail",
      directoryFormat: "legacy",
      projectMemoryDirectoryPath: join(workspace.fsPath, ".trail")
    });
  });

  it("uses canonical paths for a new workspace with no project memory directory", async () => {
    const workspace = await createWorkspace("example", "none");

    expect(resolveProjectMemoryPaths(workspace)).toEqual(canonicalProjectMemoryPaths(workspace));
  });

  it("keeps the legacy and canonical path helpers explicit", async () => {
    const workspace = await createWorkspace("example", "none");

    expect(canonicalProjectMemoryPaths(workspace).projectMarkerPath).toBe(join(workspace.fsPath, ".waymark", "project.json"));
    expect(legacyProjectMemoryPaths(workspace).projectMarkerPath).toBe(join(workspace.fsPath, ".trail", "project.json"));
  });

  it("uses one watcher pattern covering canonical and legacy project-memory directories", () => {
    expect(PROJECT_MEMORY_WATCH_PATTERN).toBe("{.waymark,.trail}/**");
  });

  it("migrates legacy project memory by copying supported files without deleting .trail", async () => {
    const workspace = await createWorkspace("example", "legacy");
    await writeFile(join(workspace.fsPath, ".trail", "project.json"), '{"schemaVersion":1,"projectId":"legacy","name":"example"}', "utf8");
    await writeFile(join(workspace.fsPath, ".trail", "constitution.md"), "# Project Constitution\n", "utf8");
    await mkdir(join(workspace.fsPath, ".trail", "rules"), { recursive: true });
    await mkdir(join(workspace.fsPath, ".trail", "decisions"), { recursive: true });
    await writeFile(join(workspace.fsPath, ".trail", "rules", "no-redis.md"), "# No Redis\n", "utf8");
    await writeFile(join(workspace.fsPath, ".trail", "decisions", "ADR-001-postgres.md"), "# ADR-001: PostgreSQL\n", "utf8");

    const result = await migrateLegacyProjectMemoryToCanonical(workspace);

    expect(result.sourcePath).toBe(join(workspace.fsPath, ".trail"));
    expect(result.destinationPath).toBe(join(workspace.fsPath, ".waymark"));
    await expect(readFile(join(workspace.fsPath, ".waymark", "project.json"), "utf8")).resolves.toContain("legacy");
    await expect(readFile(join(workspace.fsPath, ".waymark", "constitution.md"), "utf8")).resolves.toContain("Project Constitution");
    await expect(readFile(join(workspace.fsPath, ".waymark", "rules", "no-redis.md"), "utf8")).resolves.toContain("No Redis");
    await expect(readFile(join(workspace.fsPath, ".waymark", "decisions", "ADR-001-postgres.md"), "utf8")).resolves.toContain("PostgreSQL");
    await expect(pathExists(join(workspace.fsPath, ".trail", "project.json"))).resolves.toBe(true);
  });

  it("fails migration safely when .waymark already exists", async () => {
    const workspace = await createWorkspace("example", "legacy");
    await mkdir(join(workspace.fsPath, ".waymark"), { recursive: true });

    await expect(migrateLegacyProjectMemoryToCanonical(workspace)).rejects.toThrow("Waymark will not overwrite or merge");
  });

  it("keeps migration behind an explicit user approval prompt in the VS Code UI", async () => {
    const source = await readFile(join(process.cwd(), "src", "views", "project-memory-tree.ts"), "utf8");

    expect(source).toContain("Legacy .trail project memory found. Migrate to .waymark?");
    expect(source).toContain("confirmation !== \"Migrate to .waymark\"");
    expect(source.indexOf("showWarningMessage")).toBeLessThan(source.indexOf("migrateLegacyProjectMemory(getWorkspaceFolders()"));
  });
});

type ProjectMemoryFixture = "canonical" | "legacy" | "none";

async function createWorkspace(
  name: string,
  projectMemoryFixture: ProjectMemoryFixture = "canonical"
): Promise<{ readonly name: string; readonly fsPath: string }> {
  const root = await mkdtemp(join(tmpdir(), "trail-vscode-extension-"));
  tempRoots.push(root);
  const workspacePath = join(root, name);
  await mkdir(workspacePath, { recursive: true });
  if (projectMemoryFixture !== "none") {
    await mkdir(join(workspacePath, projectMemoryFixture === "canonical" ? ".waymark" : ".trail"), { recursive: true });
  }
  return {
    name,
    fsPath: workspacePath
  };
}

async function pathExists(path: string): Promise<boolean> {
  try {
    await access(path);
    return true;
  } catch {
    return false;
  }
}
