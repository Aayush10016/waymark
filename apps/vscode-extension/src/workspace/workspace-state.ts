import { UserFacingError } from "../shared/errors";

import type { ProjectMarkerReadResult, ProjectMarkerStore, WaymarkProject, WorkspaceFolderInfo } from "./project-marker";

export type WaymarkWorkspaceState =
  | {
      readonly kind: "noWorkspace";
    }
  | {
      readonly kind: "selectWorkspace";
      readonly folders: readonly WorkspaceFolderInfo[];
    }
  | {
      readonly kind: "notInitialized";
      readonly folder: WorkspaceFolderInfo;
      readonly markerPath: string;
    }
  | {
      readonly kind: "initialized";
      readonly folder: WorkspaceFolderInfo;
      readonly markerPath: string;
      readonly project: WaymarkProject;
    }
  | {
      readonly kind: "malformedProject";
      readonly folder: WorkspaceFolderInfo;
      readonly markerPath: string;
      readonly reason: string;
    };

export class WaymarkWorkspaceService {
  private readonly markerStore: ProjectMarkerStore;

  public constructor(markerStore: ProjectMarkerStore) {
    this.markerStore = markerStore;
  }

  public async getState(
    workspaceFolders: readonly WorkspaceFolderInfo[],
    selectedFolderPath?: string
  ): Promise<WaymarkWorkspaceState> {
    const selectedFolder = selectWorkspaceFolder(workspaceFolders, selectedFolderPath);
    if (selectedFolder === "none") {
      return { kind: "noWorkspace" };
    }

    if (selectedFolder === "selectionRequired") {
      return {
        kind: "selectWorkspace",
        folders: workspaceFolders
      };
    }

    return toWorkspaceState(selectedFolder, await this.markerStore.read(selectedFolder));
  }

  public async initialize(
    workspaceFolders: readonly WorkspaceFolderInfo[],
    selectedFolderPath?: string
  ): Promise<WaymarkWorkspaceState> {
    const selectedFolder = selectWorkspaceFolder(workspaceFolders, selectedFolderPath);
    if (selectedFolder === "none") {
      throw new UserFacingError("Open a workspace before initializing Waymark.");
    }

    if (selectedFolder === "selectionRequired") {
      throw new UserFacingError("Select a workspace folder before initializing Waymark.");
    }

    const project = await this.markerStore.initialize(selectedFolder);
    return {
      kind: "initialized",
      folder: selectedFolder,
      markerPath: this.markerStore.markerPath(selectedFolder),
      project
    };
  }
}

function selectWorkspaceFolder(
  workspaceFolders: readonly WorkspaceFolderInfo[],
  selectedFolderPath?: string
): WorkspaceFolderInfo | "none" | "selectionRequired" {
  if (workspaceFolders.length === 0) {
    return "none";
  }

  if (workspaceFolders.length === 1) {
    return workspaceFolders[0] ?? "none";
  }

  if (selectedFolderPath === undefined) {
    return "selectionRequired";
  }

  const selectedFolder = workspaceFolders.find((folder) => folder.fsPath === selectedFolderPath);
  return selectedFolder ?? "selectionRequired";
}

function toWorkspaceState(folder: WorkspaceFolderInfo, result: ProjectMarkerReadResult): WaymarkWorkspaceState {
  switch (result.status) {
    case "missing":
      return {
        kind: "notInitialized",
        folder,
        markerPath: result.markerPath
      };
    case "valid":
      return {
        kind: "initialized",
        folder,
        markerPath: result.markerPath,
        project: result.project
      };
    case "malformed":
      return {
        kind: "malformedProject",
        folder,
        markerPath: result.markerPath,
        reason: result.reason
      };
  }
}
