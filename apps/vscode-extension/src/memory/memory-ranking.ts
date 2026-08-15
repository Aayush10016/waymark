import type { ProjectMemorySearchResult, ProjectMemorySourceType } from "./models";

const SOURCE_AUTHORITY_WEIGHT: Readonly<Record<ProjectMemorySourceType, number>> = {
  PROJECT: 70,
  CONSTITUTION: 60,
  RULE: 55,
  DECISION: 50,
  GIT_COMMIT: 25,
  GIT_STATUS: 20,
  GIT_FILE: 15
};

export function sourceAuthorityWeight(sourceType: ProjectMemorySourceType): number {
  return SOURCE_AUTHORITY_WEIGHT[sourceType];
}

export function compareSearchResults(left: ProjectMemorySearchResult, right: ProjectMemorySearchResult): number {
  if (left.score !== right.score) {
    return right.score - left.score;
  }

  const authorityDifference =
    sourceAuthorityWeight(right.document.sourceType) - sourceAuthorityWeight(left.document.sourceType);
  if (authorityDifference !== 0) {
    return authorityDifference;
  }

  const workspaceDifference = left.document.workspaceFolder.name.localeCompare(right.document.workspaceFolder.name);
  if (workspaceDifference !== 0) {
    return workspaceDifference;
  }

  return left.document.title.localeCompare(right.document.title);
}
