import { constants } from "node:fs";
import { access, mkdir, readFile, writeFile } from "node:fs/promises";
import { dirname } from "node:path";

import { resolveProjectMemoryPaths } from "../workspace/project-memory-paths";

import type { WorkspaceFolderInfo } from "../workspace/project-marker";

export type ConstitutionState =
  | {
      readonly status: "missing";
      readonly path: string;
    }
  | {
      readonly status: "configured";
      readonly path: string;
      readonly title: string;
    };

export class ConstitutionStore {
  public async read(workspaceFolder: WorkspaceFolderInfo): Promise<ConstitutionState> {
    const constitutionPath = resolveProjectMemoryPaths(workspaceFolder).constitutionPath;
    if (!(await pathExists(constitutionPath))) {
      return {
        status: "missing",
        path: constitutionPath
      };
    }

    const content = await readFile(constitutionPath, "utf8");
    return {
      status: "configured",
      path: constitutionPath,
      title: content.includes("# Project Constitution") ? "Project Constitution" : "Project Constitution"
    };
  }

  public async create(workspaceFolder: WorkspaceFolderInfo): Promise<ConstitutionState> {
    const constitutionPath = resolveProjectMemoryPaths(workspaceFolder).constitutionPath;
    if (await pathExists(constitutionPath)) {
      return this.read(workspaceFolder);
    }

    await mkdir(dirname(constitutionPath), { recursive: true });
    try {
      await writeFile(constitutionPath, constitutionTemplate(workspaceFolder.name), {
        encoding: "utf8",
        flag: "wx"
      });
    } catch (error) {
      if (isNodeError(error) && error.code === "EEXIST") {
        return this.read(workspaceFolder);
      }

      throw error;
    }

    return this.read(workspaceFolder);
  }
}

export function constitutionTemplate(projectName: string): string {
  return `# Project Constitution

## Project

Name: ${projectName}

## Project Purpose

Describe what this project exists to do.

## Architectural Principles

- Add project architectural principles here.

## Technology Constraints

- Runtime:
- Language:
- Framework:
- Database:

## Required Technologies

- Add explicitly required technologies here.

## Prohibited Technologies

- Add explicitly prohibited technologies here.

## Security Principles

- Add security principles here.

## Engineering Principles

- Add engineering principles here.

## Explicit Rules

<!-- Approved project rules are defined in .waymark/rules/. -->

## Notes

<!-- Additional project-level context. -->
`;
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
