import { describe, expect, it } from "vitest";

import {
  parseCommitDetail,
  parsePorcelainStatus,
  parseRecentCommits,
  parseRepositoryInfo
} from "../src/git/git-parsers";

describe("Git parsers", () => {
  it("parses normal branch repository info", () => {
    expect(parseRepositoryInfo("/repo", "feature/auth\n", "a31f92c\n")).toEqual({
      rootPath: "/repo",
      branch: "feature/auth",
      headCommit: "a31f92c",
      isDetachedHead: false
    });
  });

  it("parses detached HEAD repository info", () => {
    expect(parseRepositoryInfo("/repo", "HEAD\n", "a31f92c\n")).toEqual({
      rootPath: "/repo",
      branch: undefined,
      headCommit: "a31f92c",
      isDetachedHead: true
    });
  });

  it("parses staged, unstaged, untracked, deleted, renamed, mixed, spaced, and special filenames", () => {
    const output = [
      "M  staged file.ts",
      " M unstaged.ts",
      "?? untracked file.ts",
      " D deleted.ts",
      "R  old name.ts",
      "new name.ts",
      "MM mixed.ts",
      " M special [file] #1.ts",
      ""
    ].join("\0");

    expect(parsePorcelainStatus(output)).toEqual([
      {
        path: "staged file.ts",
        previousPath: undefined,
        staged: true,
        unstaged: false,
        untracked: false,
        deleted: false,
        renamed: false
      },
      {
        path: "unstaged.ts",
        previousPath: undefined,
        staged: false,
        unstaged: true,
        untracked: false,
        deleted: false,
        renamed: false
      },
      {
        path: "untracked file.ts",
        previousPath: undefined,
        staged: false,
        unstaged: false,
        untracked: true,
        deleted: false,
        renamed: false
      },
      {
        path: "deleted.ts",
        previousPath: undefined,
        staged: false,
        unstaged: true,
        untracked: false,
        deleted: true,
        renamed: false
      },
      {
        path: "new name.ts",
        previousPath: "old name.ts",
        staged: true,
        unstaged: false,
        untracked: false,
        deleted: false,
        renamed: true
      },
      {
        path: "mixed.ts",
        previousPath: undefined,
        staged: true,
        unstaged: true,
        untracked: false,
        deleted: false,
        renamed: false
      },
      {
        path: "special [file] #1.ts",
        previousPath: undefined,
        staged: false,
        unstaged: true,
        untracked: false,
        deleted: false,
        renamed: false
      }
    ]);
  });

  it("parses recent commits", () => {
    const output = [
      "aaaaaaaa\u001fa31f92c\u001fFix authentication flow\u001fAayush Tiwari\u001f2026-08-11T10:00:00Z",
      "bbbbbbbb\u001f92bc184\u001fAdd JWT validation\u001fAayush Tiwari\u001f2026-08-10T10:00:00Z",
      ""
    ].join("\u001e");

    expect(parseRecentCommits(output)).toEqual([
      {
        sha: "aaaaaaaa",
        shortSha: "a31f92c",
        subject: "Fix authentication flow",
        author: "Aayush Tiwari",
        date: "2026-08-11T10:00:00Z"
      },
      {
        sha: "bbbbbbbb",
        shortSha: "92bc184",
        subject: "Add JWT validation",
        author: "Aayush Tiwari",
        date: "2026-08-10T10:00:00Z"
      }
    ]);
  });

  it("parses commit details and changed files", () => {
    const output = [
      "aaaaaaaa\u001fa31f92c\u001fFix authentication flow\u001fAayush Tiwari\u001f2026-08-11T10:00:00Z",
      "AuthService.java",
      "SecurityConfig.java",
      ""
    ].join("\n");

    expect(parseCommitDetail(output)).toEqual({
      sha: "aaaaaaaa",
      shortSha: "a31f92c",
      subject: "Fix authentication flow",
      author: "Aayush Tiwari",
      date: "2026-08-11T10:00:00Z",
      changedFiles: ["AuthService.java", "SecurityConfig.java"]
    });
  });
});
