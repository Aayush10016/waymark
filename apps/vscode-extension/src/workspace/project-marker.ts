import { constants } from "node:fs";
import { access, mkdir, readFile, writeFile } from "node:fs/promises";
import { dirname } from "node:path";
import { randomUUID } from "node:crypto";

import { UserFacingError } from "../shared/errors";
import { canonicalProjectMemoryPaths, resolveProjectMemoryPaths } from "./project-memory-paths";

export const WAYMARK_PROJECT_SCHEMA_VERSION = 1;

export interface WorkspaceFolderInfo {
  readonly name: string;
  readonly fsPath: string;
}

export interface WaymarkProject {
  readonly schemaVersion: typeof WAYMARK_PROJECT_SCHEMA_VERSION;
  readonly projectId: string;
  readonly name: string;
}

export type ProjectMarkerReadResult =
  | {
      readonly status: "missing";
      readonly markerPath: string;
    }
  | {
      readonly status: "valid";
      readonly markerPath: string;
      readonly project: WaymarkProject;
    }
  | {
      readonly status: "malformed";
      readonly markerPath: string;
      readonly reason: string;
    };

export class ProjectMarkerStore {
  private readonly createProjectId: () => string;

  public constructor(createProjectId: () => string = randomUUID) {
    this.createProjectId = createProjectId;
  }

  public markerPath(workspaceFolder: WorkspaceFolderInfo): string {
    return resolveProjectMemoryPaths(workspaceFolder).projectMarkerPath;
  }

  public async read(workspaceFolder: WorkspaceFolderInfo): Promise<ProjectMarkerReadResult> {
    const markerPath = this.markerPath(workspaceFolder);
    if (!(await pathExists(markerPath))) {
      return {
        status: "missing",
        markerPath
      };
    }

    try {
      const rawProject = await readFile(markerPath, "utf8");
      const parsedProject: unknown = JSON.parse(rawProject);
      const project = parseWaymarkProject(parsedProject);
      return {
        status: "valid",
        markerPath,
        project
      };
    } catch (error) {
      if (error instanceof SyntaxError || error instanceof UserFacingError) {
        return {
          status: "malformed",
          markerPath,
          reason: error.message
        };
      }

      throw error;
    }
  }

  public async initialize(workspaceFolder: WorkspaceFolderInfo): Promise<WaymarkProject> {
    const current = await this.read(workspaceFolder);
    if (current.status === "valid") {
      return current.project;
    }

    if (current.status === "malformed") {
      throw new UserFacingError(
        `Waymark cannot initialize this workspace because ${current.markerPath} already exists but is not valid JSON.`
      );
    }

    const project: WaymarkProject = {
      schemaVersion: WAYMARK_PROJECT_SCHEMA_VERSION,
      projectId: this.createProjectId(),
      name: workspaceFolder.name
    };

    const canonicalPaths = canonicalProjectMemoryPaths(workspaceFolder);
    await mkdir(dirname(canonicalPaths.projectMarkerPath), { recursive: true });
    await mkdir(canonicalPaths.rulesDirectoryPath, { recursive: true });
    await mkdir(canonicalPaths.decisionsDirectoryPath, { recursive: true });

    try {
      await writeFile(canonicalPaths.projectMarkerPath, `${JSON.stringify(project, null, 2)}\n`, {
        encoding: "utf8",
        flag: "wx"
      });
    } catch (error) {
      if (isNodeError(error) && error.code === "EEXIST") {
        const existing = await this.read(workspaceFolder);
        if (existing.status === "valid") {
          return existing.project;
        }
      }

      throw error;
    }

    return project;
  }
}

function parseWaymarkProject(value: unknown): WaymarkProject {
  if (!isRecord(value)) {
    throw new UserFacingError("Waymark project marker must be a JSON object.");
  }

  if (value.schemaVersion !== WAYMARK_PROJECT_SCHEMA_VERSION) {
    throw new UserFacingError("Waymark project marker uses an unsupported schema version.");
  }

  if (typeof value.projectId !== "string" || value.projectId.trim().length === 0) {
    throw new UserFacingError("Waymark project marker is missing projectId.");
  }

  if (typeof value.name !== "string" || value.name.trim().length === 0) {
    throw new UserFacingError("Waymark project marker is missing name.");
  }

  return {
    schemaVersion: WAYMARK_PROJECT_SCHEMA_VERSION,
    projectId: value.projectId,
    name: value.name
  };
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}

async function pathExists(path: string): Promise<boolean> {
  try {
    await access(path, constants.F_OK);
    return true;
  } catch (error) {
    if (isNodeError(error) && error.code === "ENOENT") {
      return false;
    }

    throw error;
  }
}

function isNodeError(error: unknown): error is NodeJS.ErrnoException {
  return error instanceof Error && "code" in error;
}
