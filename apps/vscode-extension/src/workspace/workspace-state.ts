import { UserFacingError } from "../shared/errors";
import {
  migrateLegacyProjectMemoryToCanonical,
  resolveProjectMemoryPaths,
  type ProjectMemoryDirectoryFormat,
  type ProjectMemoryMigrationResult
} from "./project-memory-paths";

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
      readonly projectMemoryDirectoryPath: string;
      readonly projectMemoryDirectoryFormat: ProjectMemoryDirectoryFormat;
    }
  | {
      readonly kind: "initialized";
      readonly folder: WorkspaceFolderInfo;
      readonly markerPath: string;
      readonly projectMemoryDirectoryPath: string;
      readonly projectMemoryDirectoryFormat: ProjectMemoryDirectoryFormat;
      readonly project: WaymarkProject;
    }
  | {
      readonly kind: "malformedProject";
      readonly folder: WorkspaceFolderInfo;
      readonly markerPath: string;
      readonly projectMemoryDirectoryPath: string;
      readonly projectMemoryDirectoryFormat: ProjectMemoryDirectoryFormat;
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
      projectMemoryDirectoryPath: resolveProjectMemoryPaths(selectedFolder).projectMemoryDirectoryPath,
      projectMemoryDirectoryFormat: resolveProjectMemoryPaths(selectedFolder).directoryFormat,
      project
    };
  }

  public async migrateLegacyProjectMemory(
    workspaceFolders: readonly WorkspaceFolderInfo[],
    selectedFolderPath?: string
  ): Promise<ProjectMemoryMigrationResult> {
    const selectedFolder = selectWorkspaceFolder(workspaceFolders, selectedFolderPath);
    if (selectedFolder === "none") {
      throw new UserFacingError("Open a workspace before migrating Waymark project memory.");
    }

    if (selectedFolder === "selectionRequired") {
      throw new UserFacingError("Select a workspace folder before migrating Waymark project memory.");
    }

    return migrateLegacyProjectMemoryToCanonical(selectedFolder);
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
  const paths = resolveProjectMemoryPaths(folder);
  switch (result.status) {
    case "missing":
      return {
        kind: "notInitialized",
        folder,
        markerPath: result.markerPath,
        projectMemoryDirectoryPath: paths.projectMemoryDirectoryPath,
        projectMemoryDirectoryFormat: paths.directoryFormat
      };
    case "valid":
      return {
        kind: "initialized",
        folder,
        markerPath: result.markerPath,
        projectMemoryDirectoryPath: paths.projectMemoryDirectoryPath,
        projectMemoryDirectoryFormat: paths.directoryFormat,
        project: result.project
      };
    case "malformed":
      return {
        kind: "malformedProject",
        folder,
        markerPath: result.markerPath,
        projectMemoryDirectoryPath: paths.projectMemoryDirectoryPath,
        projectMemoryDirectoryFormat: paths.directoryFormat,
        reason: result.reason
      };
  }
}
