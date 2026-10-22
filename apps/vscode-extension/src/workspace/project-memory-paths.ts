import { constants, existsSync, statSync } from "node:fs";
import { access, cp, mkdir, stat } from "node:fs/promises";
import { join } from "node:path";

import { UserFacingError } from "../shared/errors";

import type { WorkspaceFolderInfo } from "./project-marker";

export const CANONICAL_PROJECT_MEMORY_DIRECTORY_NAME = ".waymark";
export const LEGACY_PROJECT_MEMORY_DIRECTORY_NAME = ".trail";
export const PROJECT_MEMORY_WATCH_PATTERN = "{.waymark,.trail}/**";

export type ProjectMemoryDirectoryFormat = "canonical" | "legacy";

export function isProjectMemoryPath(relativePath: string): boolean {
  const directory = relativePath.replace(/\\/g, "/").split("/")[0];
  return directory === CANONICAL_PROJECT_MEMORY_DIRECTORY_NAME || directory === LEGACY_PROJECT_MEMORY_DIRECTORY_NAME;
}

export interface ProjectMemoryPaths {
  readonly directoryName: typeof CANONICAL_PROJECT_MEMORY_DIRECTORY_NAME | typeof LEGACY_PROJECT_MEMORY_DIRECTORY_NAME;
  readonly directoryFormat: ProjectMemoryDirectoryFormat;
  readonly projectMemoryDirectoryPath: string;
  readonly projectMarkerPath: string;
  readonly constitutionPath: string;
  readonly rulesDirectoryPath: string;
  readonly decisionsDirectoryPath: string;
}

export interface ProjectMemoryMigrationResult {
  readonly sourcePath: string;
  readonly destinationPath: string;
}

export function resolveProjectMemoryPaths(workspaceFolder: WorkspaceFolderInfo): ProjectMemoryPaths {
  const canonical = canonicalProjectMemoryPaths(workspaceFolder);
  if (directoryExists(canonical.projectMemoryDirectoryPath)) {
    return canonical;
  }

  const legacy = legacyProjectMemoryPaths(workspaceFolder);
  if (directoryExists(legacy.projectMemoryDirectoryPath)) {
    return legacy;
  }

  return canonical;
}

export function canonicalProjectMemoryPaths(workspaceFolder: WorkspaceFolderInfo): ProjectMemoryPaths {
  return projectMemoryPaths(workspaceFolder, CANONICAL_PROJECT_MEMORY_DIRECTORY_NAME, "canonical");
}

export function legacyProjectMemoryPaths(workspaceFolder: WorkspaceFolderInfo): ProjectMemoryPaths {
  return projectMemoryPaths(workspaceFolder, LEGACY_PROJECT_MEMORY_DIRECTORY_NAME, "legacy");
}

export async function migrateLegacyProjectMemoryToCanonical(
  workspaceFolder: WorkspaceFolderInfo
): Promise<ProjectMemoryMigrationResult> {
  const legacy = legacyProjectMemoryPaths(workspaceFolder);
  const canonical = canonicalProjectMemoryPaths(workspaceFolder);

  if (!(await pathExists(legacy.projectMemoryDirectoryPath))) {
    throw new UserFacingError("No legacy .trail project memory directory was found.");
  }

  if (!(await isDirectory(legacy.projectMemoryDirectoryPath))) {
    throw new UserFacingError("Legacy .trail project memory path exists but is not a directory.");
  }

  if (await pathExists(canonical.projectMemoryDirectoryPath)) {
    throw new UserFacingError(
      "Cannot migrate legacy .trail project memory because .waymark already exists. Waymark will not overwrite or merge project memory automatically."
    );
  }

  await mkdir(workspaceFolder.fsPath, { recursive: true });
  await cp(legacy.projectMemoryDirectoryPath, canonical.projectMemoryDirectoryPath, {
    recursive: true,
    errorOnExist: true,
    force: false
  });

  await validateMigratedProjectMemory(legacy, canonical);

  return {
    sourcePath: legacy.projectMemoryDirectoryPath,
    destinationPath: canonical.projectMemoryDirectoryPath
  };
}

function projectMemoryPaths(
  workspaceFolder: WorkspaceFolderInfo,
  directoryName: ProjectMemoryPaths["directoryName"],
  directoryFormat: ProjectMemoryDirectoryFormat
): ProjectMemoryPaths {
  const projectMemoryDirectoryPath = join(workspaceFolder.fsPath, directoryName);
  return {
    directoryName,
    directoryFormat,
    projectMemoryDirectoryPath,
    projectMarkerPath: join(projectMemoryDirectoryPath, "project.json"),
    constitutionPath: join(projectMemoryDirectoryPath, "constitution.md"),
    rulesDirectoryPath: join(projectMemoryDirectoryPath, "rules"),
    decisionsDirectoryPath: join(projectMemoryDirectoryPath, "decisions")
  };
}

async function validateMigratedProjectMemory(source: ProjectMemoryPaths, destination: ProjectMemoryPaths): Promise<void> {
  await validateCopiedPath(source.projectMarkerPath, destination.projectMarkerPath);
  await validateCopiedPath(source.constitutionPath, destination.constitutionPath);
  await validateCopiedPath(source.rulesDirectoryPath, destination.rulesDirectoryPath);
  await validateCopiedPath(source.decisionsDirectoryPath, destination.decisionsDirectoryPath);
}

async function validateCopiedPath(sourcePath: string, destinationPath: string): Promise<void> {
  if (await pathExists(sourcePath) && !(await pathExists(destinationPath))) {
    throw new UserFacingError("Waymark could not validate the migrated .waymark project memory.");
  }
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

async function isDirectory(path: string): Promise<boolean> {
  return (await stat(path)).isDirectory();
}

function directoryExists(path: string): boolean {
  try {
    return statSync(path).isDirectory();
  } catch (error) {
    if (isNodeError(error) && error.code === "ENOENT") {
      return false;
    }

    if (!existsSync(path)) {
      return false;
    }

    throw error;
  }
}

function isNodeError(error: unknown): error is NodeJS.ErrnoException {
  return error instanceof Error && "code" in error;
}
