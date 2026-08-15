import type { GitCommitDetail, GitCommitSummary, GitFileStatus, GitRepositoryInfo } from "./models";

const FIELD_SEPARATOR = "\u001f";
const RECORD_SEPARATOR = "\u001e";

export function parseRepositoryInfo(rootPath: string, branchOutput: string, headCommitOutput: string): GitRepositoryInfo {
  const branch = branchOutput.trim();
  const headCommit = headCommitOutput.trim();
  const isDetachedHead = branch === "HEAD";

  return {
    rootPath,
    branch: isDetachedHead ? undefined : branch,
    headCommit,
    isDetachedHead
  };
}

export function parsePorcelainStatus(output: string): readonly GitFileStatus[] {
  const records = output.split("\0").filter((record) => record.length > 0);
  const statuses: GitFileStatus[] = [];

  for (let index = 0; index < records.length; index += 1) {
    const record = records[index];
    if (record === undefined || record.length < 4) {
      continue;
    }

    const stagedCode = record.charAt(0);
    const unstagedCode = record.charAt(1);
    const firstPath = record.slice(3);
    const renamed = stagedCode === "R" || unstagedCode === "R";
    const path = renamed ? records[index + 1] ?? firstPath : firstPath;
    const previousPath = renamed ? firstPath : undefined;

    if (renamed) {
      index += 1;
    }

    statuses.push({
      path,
      previousPath,
      staged: stagedCode !== " " && stagedCode !== "?",
      unstaged: unstagedCode !== " " && unstagedCode !== "?",
      untracked: stagedCode === "?" && unstagedCode === "?",
      deleted: stagedCode === "D" || unstagedCode === "D",
      renamed,
    });
  }

  return statuses;
}

export function parseRecentCommits(output: string): readonly GitCommitSummary[] {
  return splitCommitRecords(output).map((record) => {
    const fields = record.split(FIELD_SEPARATOR);
    return {
      sha: fields[0] ?? "",
      shortSha: fields[1] ?? "",
      subject: fields[2] ?? "",
      author: fields[3] ?? "",
      date: fields[4] ?? "",
    };
  });
}

export function parseCommitDetail(output: string): GitCommitDetail {
  const [header = "", ...changedFiles] = output.split(/\r?\n/);
  const fields = header.split(FIELD_SEPARATOR);
  return {
    sha: fields[0] ?? "",
    shortSha: fields[1] ?? "",
    subject: fields[2] ?? "",
    author: fields[3] ?? "",
    date: fields[4] ?? "",
    changedFiles: changedFiles.map((file) => file.trim()).filter((file) => file.length > 0),
  };
}

function splitCommitRecords(output: string): string[] {
  return output
    .split(RECORD_SEPARATOR)
    .map((record) => record.trim())
    .filter((record) => record.length > 0);
}
