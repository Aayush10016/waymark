import { mkdtemp, rm, writeFile } from "node:fs/promises";
import { join } from "node:path";
import { tmpdir } from "node:os";

import { afterEach, describe, expect, it } from "vitest";

import { ProjectMarkerStore } from "../src/workspace/project-marker";
import { WaymarkWorkspaceService } from "../src/workspace/workspace-state";

const tempRoots: string[] = [];

describe("WaymarkWorkspaceService", () => {
  afterEach(async () => {
    await Promise.all(tempRoots.map((path) => rm(path, { recursive: true, force: true })));
    tempRoots.length = 0;
  });

  it("returns no workspace state when no folders are open", async () => {
    const service = new WaymarkWorkspaceService(new ProjectMarkerStore(() => "project-1"));

    await expect(service.getState([])).resolves.toEqual({
      kind: "noWorkspace"
    });
  });

  it("returns not initialized state for a single-root workspace without a marker", async () => {
    const workspace = await createWorkspace("example");
    const service = new WaymarkWorkspaceService(new ProjectMarkerStore(() => "project-1"));

    await expect(service.getState([workspace])).resolves.toMatchObject({
      kind: "notInitialized",
      folder: workspace
    });
  });

  it("returns initialized state for a single-root workspace with a valid marker", async () => {
    const workspace = await createWorkspace("example");
    await writeFile(
      join(workspace.fsPath, ".trail", "project.json"),
      JSON.stringify({ schemaVersion: 1, projectId: "existing-id", name: "example" }),
      "utf8"
    );
    const service = new WaymarkWorkspaceService(new ProjectMarkerStore(() => "project-1"));

    await expect(service.getState([workspace])).resolves.toMatchObject({
      kind: "initialized",
      project: {
        projectId: "existing-id"
      }
    });
  });

  it("requires folder selection for multi-root workspaces", async () => {
    const frontend = await createWorkspace("frontend");
    const backend = await createWorkspace("backend");
    const service = new WaymarkWorkspaceService(new ProjectMarkerStore(() => "project-1"));

    await expect(service.getState([frontend, backend])).resolves.toEqual({
      kind: "selectWorkspace",
      folders: [frontend, backend]
    });
  });

  it("initializes only the selected folder in a multi-root workspace", async () => {
    const frontend = await createWorkspace("frontend");
    const backend = await createWorkspace("backend");
    const service = new WaymarkWorkspaceService(new ProjectMarkerStore(() => "project-1"));

    await expect(service.initialize([frontend, backend], backend.fsPath)).resolves.toMatchObject({
      kind: "initialized",
      folder: backend,
      project: {
        name: "backend"
      }
    });
    await expect(service.getState([frontend, backend], backend.fsPath)).resolves.toMatchObject({
      kind: "initialized",
      folder: backend
    });
    await expect(service.getState([frontend, backend], frontend.fsPath)).resolves.toMatchObject({
      kind: "notInitialized",
      folder: frontend
    });
  });

  it("refreshes sidebar state after initialization through the workspace service", async () => {
    const workspace = await createWorkspace("example");
    const service = new WaymarkWorkspaceService(new ProjectMarkerStore(() => "project-1"));

    await expect(service.getState([workspace])).resolves.toMatchObject({
      kind: "notInitialized"
    });
    await service.initialize([workspace]);
    await expect(service.getState([workspace])).resolves.toMatchObject({
      kind: "initialized",
      project: {
        projectId: "project-1"
      }
    });
  });

  it("returns malformed state for invalid JSON", async () => {
    const workspace = await createWorkspace("example");
    await writeFile(join(workspace.fsPath, ".trail", "project.json"), "{", "utf8");
    const service = new WaymarkWorkspaceService(new ProjectMarkerStore(() => "project-1"));

    await expect(service.getState([workspace])).resolves.toMatchObject({
      kind: "malformedProject"
    });
  });
});

async function createWorkspace(name: string): Promise<{ readonly name: string; readonly fsPath: string }> {
  const root = await mkdtemp(join(tmpdir(), "trail-vscode-extension-"));
  tempRoots.push(root);
  const workspacePath = join(root, name);
  await import("node:fs/promises").then(({ mkdir }) => mkdir(join(workspacePath, ".trail"), { recursive: true }));
  return {
    name,
    fsPath: workspacePath
  };
}
