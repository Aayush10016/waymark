import { extname } from "node:path";

import type { CodeContext } from "../code-context/models";
import type { GitFileStatus } from "../git/models";
import type { ExplicitRule } from "../project-source/rules";
import type { WorkspaceFolderInfo } from "../workspace/project-marker";

export interface GuardianScopeTarget {
  readonly workspaceFolder: WorkspaceFolderInfo;
  readonly codeContext?: CodeContext;
  readonly changedFiles: readonly GitFileStatus[];
}

export function ruleAppliesToGuardianTarget(rule: ExplicitRule, target: GuardianScopeTarget): boolean {
  const scope = normalizeScope(rule.scope);
  if (scope === "project-wide" || scope === "global" || scope === "all" || scope === "") {
    return true;
  }

  if (!SUPPORTED_SCOPES.has(scope)) {
    return false;
  }

  const files = target.codeContext === undefined
    ? target.changedFiles.map((file) => file.path)
    : [target.codeContext.relativePath];
  const language = target.codeContext?.language.toLowerCase();
  const workspaceName = target.workspaceFolder.name.toLowerCase();

  return files.some((file) => pathMatchesScope(scope, file, workspaceName, language));
}

function pathMatchesScope(scope: string, relativePath: string, workspaceName: string, language: string | undefined): boolean {
  const normalizedPath = relativePath.replace(/\\/g, "/").toLowerCase();
  const pathParts = new Set(normalizedPath.split("/").filter((part) => part.length > 0));
  const extension = extname(normalizedPath);

  switch (scope) {
    case "backend":
      return workspaceName.includes("backend") || pathParts.has("backend") || normalizedPath.startsWith("apps/backend/");
    case "frontend":
      return workspaceName.includes("frontend") || pathParts.has("frontend") || normalizedPath.startsWith("apps/frontend/");
    case "extension":
      return workspaceName.includes("extension") || pathParts.has("extension") || normalizedPath.startsWith("apps/extension/");
    case "vscode":
      return workspaceName.includes("vscode") || normalizedPath.includes("vscode-extension/");
    case "java":
      return language === "java" || extension === ".java";
    case "typescript":
      return language === "typescript" || extension === ".ts" || extension === ".tsx";
    case "javascript":
      return language === "javascript" || extension === ".js" || extension === ".jsx";
    case "python":
      return language === "python" || extension === ".py";
    default:
      return false;
  }
}

function normalizeScope(scope: string): string {
  return scope.trim().toLowerCase();
}

const SUPPORTED_SCOPES = new Set([
  "backend",
  "frontend",
  "extension",
  "vscode",
  "java",
  "typescript",
  "javascript",
  "python"
]);
