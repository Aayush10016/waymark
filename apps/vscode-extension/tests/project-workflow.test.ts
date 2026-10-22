import { mkdtemp, rm, rename, access } from "node:fs/promises";
import { join } from "node:path";
import { tmpdir } from "node:os";

import { describe, expect, it } from "vitest";

import { GitCliClient } from "../src/git/git-client";
import { AutomaticGuardianRunner } from "../src/guardian/automatic-guardian-runner";
import { ProjectMemoryCollector } from "../src/memory/memory-collector";
import { ProjectMemoryRepository } from "../src/memory/memory-repository";
import { DeterministicMemoryRetriever } from "../src/memory/memory-retriever";
import { ConstitutionStore } from "../src/project-source/constitution";
import { DecisionStore } from "../src/project-source/decisions";
import { RuleStore } from "../src/project-source/rules";
import { ProjectMarkerStore } from "../src/workspace/project-marker";
import { migrateLegacyProjectMemoryToCanonical } from "../src/workspace/project-memory-paths";

describe("Waymark project workflow with real local stores", () => {
  it("initializes, creates evidence, retrieves rules, detects and clears violations, and preserves legacy memory", async () => {
    const root = await mkdtemp(join(tmpdir(), "waymark-workflow-"));
    const workspace = { name: "backend", fsPath: root };
    try {
      const marker = new ProjectMarkerStore();
      await marker.initialize(workspace);
      await expect(access(join(root, ".trail"))).rejects.toThrow();
      await new ConstitutionStore().create(workspace);
      const rules = new RuleStore();
      const decisions = new DecisionStore();
      const rule = await rules.createFromInput(workspace, {
        title: "No Redis", type: "technology", scope: "project-wide",
        ruleText: "Do not use Redis.", reason: "Keep infrastructure simple.", source: "Release test"
      });
      await decisions.createFromInput(workspace, {
        title: "Use PostgreSQL", status: "Accepted", decision: "Use PostgreSQL for persistence."
      });
      const memory = new ProjectMemoryRepository(new ProjectMemoryCollector(new GitCliClient()), new DeterministicMemoryRetriever());
      expect((await memory.search("Redis", [workspace])).some(result => result.document.sourceType === "RULE")).toBe(true);
      const guardian = new AutomaticGuardianRunner(rules, decisions, memory);
      const file = { workspaceFolder: workspace, filePath: join(root, "service.ts"), relativePath: "service.ts", content: "import Redis from 'redis';" };
      expect((await guardian.checkFiles([file])).findings).toHaveLength(1);
      expect((await guardian.checkFiles([{ ...file, content: "export const value = 1;" }])).findings).toHaveLength(0);
      await rename(join(root, ".waymark"), join(root, ".trail"));
      memory.clear();
      expect((await guardian.checkFiles([file])).findings).toHaveLength(1);
      await migrateLegacyProjectMemoryToCanonical(workspace);
      expect((await marker.read(workspace)).status).toBe("valid");
      expect((await rules.list(workspace))[0]?.id).toBe(rule.id);
      expect((await decisions.list(workspace))[0]?.status).toBe("Accepted");
      await access(join(root, ".trail", "project.json"));
      await rules.delete(workspace, rule.id);
      expect(await rules.list(workspace)).toEqual([]);
    } finally {
      await rm(root, { recursive: true, force: true });
    }
  }, 20_000);
});
