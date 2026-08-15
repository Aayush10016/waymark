import { describe, expect, it } from "vitest";

import { GitCliClient, GitReadError, assertReadOnlyGitArgs } from "../src/git/git-client";

import type { GitProcessRunner } from "../src/git/git-client";
import type { WorkspaceFolderInfo } from "../src/workspace/project-marker";

const workspaceFolder: WorkspaceFolderInfo = {
  name: "repo",
  fsPath: "/repo"
};

describe("GitCliClient", () => {
  it("detects repository info using fixed read-only commands", async () => {
    const runner = new RecordingGitRunner([
      "true\n",
      "/repo\n",
      "main\n",
      "a31f92c\n"
    ]);
    const client = new GitCliClient(runner);

    await expect(client.getRepositoryInfo(workspaceFolder)).resolves.toMatchObject({
      rootPath: "/repo",
      branch: "main",
      headCommit: "a31f92c"
    });
    expect(runner.calls).toEqual([
      ["rev-parse", "--is-inside-work-tree"],
      ["rev-parse", "--show-toplevel"],
      ["rev-parse", "--abbrev-ref", "HEAD"],
      ["rev-parse", "--short", "HEAD"]
    ]);
  });

  it("reads status, commits, commit details, and file revision content with fixed operations", async () => {
    const runner = new RecordingGitRunner([
      "M  file.ts\0",
      "aaaaaaaa\u001fa31f92c\u001fSubject\u001fAuthor\u001f2026-08-11T10:00:00Z\u001e",
      "aaaaaaaa\u001fa31f92c\u001fSubject\u001fAuthor\u001f2026-08-11T10:00:00Z\nfile.ts\n",
      "content"
    ]);
    const client = new GitCliClient(runner);

    await client.getStatus(workspaceFolder);
    await client.getRecentCommits(workspaceFolder);
    await client.getCommitDetails(workspaceFolder, "a31f92c");
    await client.getFileRevisionContent(workspaceFolder, "file.ts", "HEAD");

    expect(runner.calls.map((call) => call[0])).toEqual(["status", "log", "show", "show"]);
  });

  it("rejects mutating Git commands", () => {
    for (const operation of ["commit", "checkout", "reset", "push", "pull", "merge", "rebase", "stash", "add", "restore"]) {
      expect(() => assertReadOnlyGitArgs([operation])).toThrow(GitReadError);
    }
  });

  it("rejects invalid commit references and unsafe paths", async () => {
    const client = new GitCliClient(new RecordingGitRunner([]));

    await expect(client.getCommitDetails(workspaceFolder, "HEAD;rm")).rejects.toThrow("Invalid Git object reference.");
    await expect(client.getFileRevisionContent(workspaceFolder, "file.ts", "HEAD^")).resolves.toBe("");
    await expect(client.getFileRevisionContent(workspaceFolder, "../secret", "HEAD")).rejects.toThrow(
      "Invalid Git file path."
    );
  });

  it("does not expose arbitrary Git command execution", () => {
    const client = new GitCliClient(new RecordingGitRunner([]));

    expect("runGitCommand" in client).toBe(false);
  });
});

class RecordingGitRunner implements GitProcessRunner {
  public readonly calls: string[][] = [];
  private readonly responses: string[];

  public constructor(responses: string[]) {
    this.responses = [...responses];
  }

  public async run(_workspaceFolder: WorkspaceFolderInfo, args: readonly string[]): Promise<string> {
    await Promise.resolve();
    this.calls.push([...args]);
    return this.responses.shift() ?? "";
  }
}
