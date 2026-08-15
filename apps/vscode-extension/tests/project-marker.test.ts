import { mkdtemp, readFile, rm, writeFile } from "node:fs/promises";
import { join } from "node:path";
import { tmpdir } from "node:os";

import { afterEach, describe, expect, it } from "vitest";

import { ProjectMarkerStore } from "../src/workspace/project-marker";

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
      join(workspace.fsPath, ".trail", "project.json"),
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

  it("initializes a workspace by creating .trail/project.json", async () => {
    const workspace = await createWorkspace("example");
    const store = new ProjectMarkerStore(() => "project-1");

    const project = await store.initialize(workspace);

    expect(project).toEqual({
      schemaVersion: 1,
      projectId: "project-1",
      name: "example"
    });
    await expect(readFile(join(workspace.fsPath, ".trail", "project.json"), "utf8")).resolves.toContain(
      '"projectId": "project-1"'
    );
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
    const markerPath = join(workspace.fsPath, ".trail", "project.json");
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
    await writeFile(join(workspace.fsPath, ".trail", "project.json"), "{", "utf8");
    const store = new ProjectMarkerStore(() => "project-1");

    await expect(store.read(workspace)).resolves.toMatchObject({
      status: "malformed"
    });
    await expect(store.initialize(workspace)).rejects.toThrow(
      "Waymark cannot initialize this workspace because .trail/project.json already exists but is not valid JSON."
    );
  });
});

async function createWorkspace(name: string): Promise<{ readonly name: string; readonly fsPath: string }> {
  const root = await mkdtemp(join(tmpdir(), "trail-vscode-extension-"));
  tempRoots.push(root);
  const workspacePath = join(root, name);
  await writeFile(join(root, ".keep"), "", "utf8");
  await import("node:fs/promises").then(({ mkdir }) => mkdir(join(workspacePath, ".trail"), { recursive: true }));
  return {
    name,
    fsPath: workspacePath
  };
}
