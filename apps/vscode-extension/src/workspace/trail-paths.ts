import { join } from "node:path";

import type { WorkspaceFolderInfo } from "./project-marker";

export interface WaymarkWorkspacePaths {
  readonly trailDirectoryPath: string;
  readonly projectMarkerPath: string;
  readonly constitutionPath: string;
  readonly rulesDirectoryPath: string;
  readonly decisionsDirectoryPath: string;
}

export function resolveWaymarkWorkspacePaths(workspaceFolder: WorkspaceFolderInfo): WaymarkWorkspacePaths {
  const trailDirectoryPath = join(workspaceFolder.fsPath, ".trail");
  return {
    trailDirectoryPath,
    projectMarkerPath: join(trailDirectoryPath, "project.json"),
    constitutionPath: join(trailDirectoryPath, "constitution.md"),
    rulesDirectoryPath: join(trailDirectoryPath, "rules"),
    decisionsDirectoryPath: join(trailDirectoryPath, "decisions")
  };
}
