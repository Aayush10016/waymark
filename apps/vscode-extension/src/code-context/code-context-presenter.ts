import type { CodeContextPackage } from "./models";

export function formatCodeContextPackage(contextPackage: CodeContextPackage): string {
  const code = contextPackage.codeContext;
  const git = contextPackage.gitEvidence;

  return [
    "TRAIL CODE CONTEXT",
    "",
    "Local code context prepared. No AI was used.",
    "",
    "CODE",
    `Selected: ${code.symbolName ?? "Selected code"}`,
    `File: ${code.relativePath}`,
    `Language: ${code.language}`,
    `Lines: ${code.startLine}-${code.endLine}`,
    code.isSelectionPreviewTruncated ? "Selection preview truncated for display." : "",
    "",
    "Selected Code",
    code.selectedTextPreview,
    "",
    "Surrounding Code",
    `Lines: ${code.surroundingStartLine}-${code.surroundingEndLine}`,
    code.surroundingCode,
    "",
    "Imports",
    ...(code.relatedImports.length > 0 ? code.relatedImports : ["(none found)"]),
    "",
    "PROJECT EVIDENCE",
    ...formatMemoryResults(contextPackage.projectEvidence),
    "",
    "GIT EVIDENCE",
    `Branch: ${git.repository?.branch ?? git.repository?.headCommit ?? "(unavailable)"}`,
    `File changed: ${git.fileStatus === undefined ? "no" : "yes"}`,
    git.unavailableReason === undefined ? "" : `Git unavailable: ${git.unavailableReason}`,
    "Relevant commits:",
    ...(git.relevantCommits.length > 0
      ? git.relevantCommits.map((commit) => `${commit.shortSha} ${commit.subject}`)
      : ["(none found)"]),
    "",
    "MEMORY EVIDENCE",
    ...formatMemoryResults(contextPackage.memoryResults)
  ]
    .filter((line) => line !== "")
    .join("\n");
}

function formatMemoryResults(results: CodeContextPackage["memoryResults"]): readonly string[] {
  if (results.length === 0) {
    return ["(none found)"];
  }

  return results.map((result) => {
    return `${result.document.sourceType}: ${result.document.title} [matched: ${result.matchedFields.join(", ")}]`;
  });
}
