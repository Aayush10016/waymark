import { describe, expect, it } from "vitest";

import { AutomaticGuardianFindingState } from "../src/guardian/automatic-guardian-state";

import type { GuardianFinding } from "../src/guardian/models";

describe("AutomaticGuardianFindingState", () => {
  it("replaces previous findings for a file when the file is rechecked clean", () => {
    const state = new AutomaticGuardianFindingState();
    const fileKey = "/workspace/backend/src/Foo.java";

    state.applyFileResult({
      fileKey,
      generation: state.beginFileCheck(fileKey),
      findings: [finding({ id: "redis", codePath: fileKey })]
    });
    const cleanUpdate = state.applyFileResult({
      fileKey,
      generation: state.beginFileCheck(fileKey),
      findings: []
    });

    expect(cleanUpdate.applied).toBe(true);
    expect(cleanUpdate.findings).toEqual([]);
    expect(state.allFindings()).toEqual([]);
  });

  it("keeps unrelated file findings when one file is fixed", () => {
    const state = new AutomaticGuardianFindingState();
    const firstFile = "/workspace/backend/src/A.java";
    const secondFile = "/workspace/backend/src/B.java";
    const secondFinding = finding({ id: "java-version", codePath: secondFile, ruleId: "java-version" });

    state.applyFileResult({
      fileKey: firstFile,
      generation: state.beginFileCheck(firstFile),
      findings: [finding({ id: "redis", codePath: firstFile })]
    });
    state.applyFileResult({
      fileKey: secondFile,
      generation: state.beginFileCheck(secondFile),
      findings: [secondFinding]
    });
    state.applyFileResult({
      fileKey: firstFile,
      generation: state.beginFileCheck(firstFile),
      findings: []
    });

    expect(state.allFindings()).toEqual([secondFinding]);
  });

  it("clears deleted file findings and rejects stale async results for the deleted file", () => {
    const state = new AutomaticGuardianFindingState();
    const fileKey = "/workspace/backend/src/Deleted.java";
    const oldGeneration = state.beginFileCheck(fileKey);

    state.clearFile(fileKey);
    const staleUpdate = state.applyFileResult({
      fileKey,
      generation: oldGeneration,
      findings: [finding({ id: "redis", codePath: fileKey })]
    });

    expect(staleUpdate.applied).toBe(false);
    expect(state.allFindings()).toEqual([]);
  });

  it("treats rename as old-file clear plus new-file evaluation", () => {
    const state = new AutomaticGuardianFindingState();
    const oldFileKey = "/workspace/backend/src/OldName.java";
    const newFileKey = "/workspace/backend/src/NewName.java";
    const newFinding = finding({ id: "redis-new", codePath: newFileKey });

    state.applyFileResult({
      fileKey: oldFileKey,
      generation: state.beginFileCheck(oldFileKey),
      findings: [finding({ id: "redis-old", codePath: oldFileKey })]
    });
    state.clearFile(oldFileKey);
    state.applyFileResult({
      fileKey: newFileKey,
      generation: state.beginFileCheck(newFileKey),
      findings: [newFinding]
    });

    expect(state.allFindings()).toEqual([newFinding]);
  });

  it("ensures latest asynchronous result wins during rapid changes", () => {
    const state = new AutomaticGuardianFindingState();
    const fileKey = "/workspace/backend/src/Rapid.java";
    const staleGeneration = state.beginFileCheck(fileKey);
    const latestGeneration = state.beginFileCheck(fileKey);

    const latestUpdate = state.applyFileResult({
      fileKey,
      generation: latestGeneration,
      findings: []
    });
    const staleUpdate = state.applyFileResult({
      fileKey,
      generation: staleGeneration,
      findings: [finding({ id: "stale-redis", codePath: fileKey })]
    });

    expect(latestUpdate.applied).toBe(true);
    expect(staleUpdate.applied).toBe(false);
    expect(state.allFindings()).toEqual([]);
  });

  it("keeps identical relative filenames isolated by workspace-qualified file keys", () => {
    const state = new AutomaticGuardianFindingState();
    const firstWorkspaceFile = "/workspace-a/src/Foo.java";
    const secondWorkspaceFile = "/workspace-b/src/Foo.java";
    const firstFinding = finding({ id: "first", codePath: firstWorkspaceFile });

    state.applyFileResult({
      fileKey: firstWorkspaceFile,
      generation: state.beginFileCheck(firstWorkspaceFile),
      findings: [firstFinding]
    });
    state.applyFileResult({
      fileKey: secondWorkspaceFile,
      generation: state.beginFileCheck(secondWorkspaceFile),
      findings: []
    });

    expect(state.allFindings()).toEqual([firstFinding]);
  });
});

function finding(overrides: Partial<GuardianFinding> = {}): GuardianFinding {
  return {
    id: "finding",
    severity: "WARNING",
    title: "Forbidden Redis dependency",
    message: "Redis is forbidden by project rules.",
    ruleId: "no-redis",
    ruleTitle: "Redis is not allowed",
    rulePath: "/workspace/.waymark/rules/no-redis.md",
    matchedTerms: ["RedisTemplate"],
    evidence: [],
    source: "AUTOMATIC_FILE_CHANGE",
    workspaceFolder: {
      name: "workspace",
      fsPath: "/workspace"
    },
    ...overrides
  };
}
