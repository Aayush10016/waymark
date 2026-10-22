import { mkdir, mkdtemp, readFile, rm, writeFile } from "node:fs/promises";
import { join } from "node:path";
import { tmpdir } from "node:os";

import { afterEach, describe, expect, it } from "vitest";

import { ConstitutionStore } from "../src/project-source/constitution";
import { DecisionStore } from "../src/project-source/decisions";
import { RuleStore } from "../src/project-source/rules";
import { ProjectMarkerStore } from "../src/workspace/project-marker";

const tempRoots: string[] = [];

describe("ConstitutionStore", () => {
  afterEach(cleanup);

  it("detects a missing constitution", async () => {
    const workspace = await createWorkspace("example");
    const store = new ConstitutionStore();

    await expect(store.read(workspace)).resolves.toMatchObject({
      status: "missing"
    });
  });

  it("detects an existing constitution", async () => {
    const workspace = await createWorkspace("example");
    await writeFile(join(workspace.fsPath, ".waymark", "constitution.md"), "# Project Constitution\n", "utf8");
    const store = new ConstitutionStore();

    await expect(store.read(workspace)).resolves.toMatchObject({
      status: "configured",
      title: "Project Constitution"
    });
  });

  it("creates a constitution without inventing technology facts", async () => {
    const workspace = await createWorkspace("example");
    const store = new ConstitutionStore();

    const constitution = await store.create(workspace);
    const saved = await readFile(constitution.path, "utf8");

    expect(saved).toContain("Name: example");
    expect(saved).toContain("- Runtime:");
    expect(saved).toContain("- Language:");
    expect(saved).toContain("- Framework:");
    expect(saved).toContain("- Database:");
    expect(saved).toContain("## Architectural Principles");
    expect(saved).toContain("## Technology Constraints");
    expect(saved).toContain("## Security Principles");
    expect(saved).toContain("## Engineering Principles");
    expect(saved).not.toContain("PostgreSQL");
  });

  it("never overwrites an existing constitution", async () => {
    const workspace = await createWorkspace("example");
    const constitutionPath = join(workspace.fsPath, ".waymark", "constitution.md");
    await writeFile(constitutionPath, "# Custom Constitution\n\nDeveloper text.\n", "utf8");
    const store = new ConstitutionStore();

    await store.create(workspace);

    await expect(readFile(constitutionPath, "utf8")).resolves.toBe("# Custom Constitution\n\nDeveloper text.\n");
  });

  it("handles arbitrary constitution Markdown safely", async () => {
    const workspace = await createWorkspace("example");
    await writeFile(join(workspace.fsPath, ".waymark", "constitution.md"), "not structured markdown", "utf8");
    const store = new ConstitutionStore();

    await expect(store.read(workspace)).resolves.toMatchObject({
      status: "configured"
    });
  });
});

describe("RuleStore", () => {
  afterEach(cleanup);

  it("handles a missing rules directory", async () => {
    const workspace = await createWorkspace("example", false);
    const store = new RuleStore();

    await expect(store.list(workspace)).resolves.toEqual([]);
  });

  it("detects a rules directory and lists Markdown rules", async () => {
    const workspace = await createWorkspace("example");
    await writeFile(
      join(workspace.fsPath, ".waymark", "rules", "no-redis.md"),
      "# No Redis\n\nStatus: active\nType: architecture\nScope: project-wide\n\n## Rule\n\nDo not add Redis.\n\n## Reason\n\nKeep infrastructure simple.\n\n## Source\n\nProject Constitution\n",
      "utf8"
    );
    const store = new RuleStore();

    await expect(store.list(workspace)).resolves.toMatchObject([
      {
        id: "no-redis",
        title: "No Redis",
        status: "active",
        type: "architecture",
        scope: "project-wide",
        ruleText: "Do not add Redis.",
        reason: "Keep infrastructure simple.",
        source: "Project Constitution",
        isActive: true
      }
    ]);
  });

  it("creates a rule Markdown file from explicit user inputs", async () => {
    const workspace = await createWorkspace("example");
    const store = new RuleStore();

    const rule = await store.create(workspace, "JWT Authentication", "security");
    const saved = await readFile(rule.path, "utf8");

    expect(rule.id).toBe("jwt-authentication");
    expect(saved).toContain("# JWT Authentication");
    expect(saved).toContain("Status: active");
    expect(saved).toContain("Type: security");
    expect(saved).toContain("<Enter rule>");
  });

  it("creates a complete rule Markdown file from guided inputs", async () => {
    const workspace = await createWorkspace("example");
    const store = new RuleStore();

    const rule = await store.createFromInput(workspace, {
      title: "Java Runtime",
      type: "technology",
      scope: "backend",
      status: "active",
      ruleText: "Backend code must use Java 17.",
      reason: "Production runtime is pinned.",
      source: "Engineering constitution"
    });
    const saved = await readFile(rule.path, "utf8");

    expect(rule.id).toBe("java-runtime");
    expect(saved).toContain("# Java Runtime");
    expect(saved).toContain("Status: active");
    expect(saved).toContain("Type: technology");
    expect(saved).toContain("Scope: backend");
    expect(saved).toContain("Backend code must use Java 17.");
    expect(saved).toContain("Production runtime is pinned.");
    expect(saved).toContain("Engineering constitution");
    expect(saved).not.toContain("<Enter rule>");
    expect(saved).not.toContain("<Why this rule exists>");
    expect(saved).not.toContain("<Source>");
  });

  it("uses duplicate-safe filenames and preserves existing rules", async () => {
    const workspace = await createWorkspace("example");
    const rulePath = join(workspace.fsPath, ".waymark", "rules", "no-redis.md");
    await writeFile(rulePath, "# Existing\n", "utf8");
    const store = new RuleStore();

    const created = await store.createFromInput(workspace, {
      title: "No Redis",
      type: "architecture",
      scope: "backend",
      status: "active",
      ruleText: "Redis must not be used.",
      reason: "Keep infrastructure simple.",
      source: "Project architecture"
    });

    expect(created.id).toBe("no-redis-2");
    await expect(readFile(rulePath, "utf8")).resolves.toBe("# Existing\n");
  });

  it("deletes an existing rule only when explicitly requested through the store", async () => {
    const workspace = await createWorkspace("example");
    const store = new RuleStore();
    const rule = await store.createFromInput(workspace, {
      title: "No Redis",
      type: "architecture",
      ruleText: "Redis must not be used.",
      reason: "Keep infrastructure simple.",
      source: "Project architecture"
    });

    await store.delete(workspace, rule.id);

    await expect(store.list(workspace)).resolves.toEqual([]);
  });

  it("handles malformed rule metadata safely", async () => {
    const workspace = await createWorkspace("example");
    await writeFile(join(workspace.fsPath, ".waymark", "rules", "broken.md"), "Status: maybe\nType: unusual\n", "utf8");
    const store = new RuleStore();

    await expect(store.list(workspace)).resolves.toMatchObject([
      {
        title: "Broken",
        status: "unknown",
        type: "unknown",
        hasMetadataWarning: true
      }
    ]);
  });

  it("does not treat deprecated rules as active", async () => {
    const workspace = await createWorkspace("example");
    await writeFile(
      join(workspace.fsPath, ".waymark", "rules", "old-rule.md"),
      "# Old Rule\n\nStatus: deprecated\nType: process\n",
      "utf8"
    );
    const store = new RuleStore();

    await expect(store.list(workspace)).resolves.toMatchObject([
      {
        status: "deprecated",
        isActive: false
      }
    ]);
  });
});

describe("DecisionStore", () => {
  afterEach(cleanup);

  it("handles a missing decisions directory", async () => {
    const workspace = await createWorkspace("example", false);
    const store = new DecisionStore();

    await expect(store.list(workspace)).resolves.toEqual([]);
  });

  it("detects a decisions directory and lists Markdown decisions", async () => {
    const workspace = await createWorkspace("example");
    await writeFile(
      join(workspace.fsPath, ".waymark", "decisions", "ADR-001-use-postgresql.md"),
      "# ADR-001: Use PostgreSQL\n\n## Status\n\nAccepted\n\n## Context\n\nNeeded persistence.\n",
      "utf8"
    );
    const store = new DecisionStore();

    await expect(store.list(workspace)).resolves.toMatchObject([
      {
        id: "ADR-001-use-postgresql",
        title: "ADR-001: Use PostgreSQL",
        status: "Accepted",
        rawStatus: "Accepted"
      }
    ]);
  });

  it("creates a sequential ADR-style decision", async () => {
    const workspace = await createWorkspace("example");
    const store = new DecisionStore();

    const decision = await store.create(workspace, "Use PostgreSQL");
    const saved = await readFile(decision.path, "utf8");

    expect(decision.id).toBe("ADR-001-use-postgresql");
    expect(saved).toContain("# ADR-001: Use PostgreSQL");
    expect(saved).toContain("Proposed");
  });

  it("creates a complete ADR from guided inputs", async () => {
    const workspace = await createWorkspace("example");
    const store = new DecisionStore();

    const decision = await store.createFromInput(workspace, {
      title: "Use AuthService",
      status: "Accepted",
      context: "Authentication logic was duplicated.",
      decision: "Route authentication through AuthService.",
      consequences: "Controllers do not validate JWTs directly."
    });
    const saved = await readFile(decision.path, "utf8");

    expect(decision.id).toBe("ADR-001-use-authservice");
    expect(saved).toContain("# ADR-001: Use AuthService");
    expect(saved).toContain("Accepted");
    expect(saved).toContain("Authentication logic was duplicated.");
    expect(saved).toContain("Route authentication through AuthService.");
    expect(saved).toContain("Controllers do not validate JWTs directly.");
  });

  it("avoids ADR numbering collisions instead of relying on file count", async () => {
    const workspace = await createWorkspace("example");
    await writeFile(join(workspace.fsPath, ".waymark", "decisions", "ADR-001-first.md"), "# ADR-001: First\n", "utf8");
    await writeFile(join(workspace.fsPath, ".waymark", "decisions", "ADR-003-third.md"), "# ADR-003: Third\n", "utf8");
    const store = new DecisionStore();

    const decision = await store.create(workspace, "Second");

    expect(decision.id).toBe("ADR-002-second");
  });

  it("never overwrites existing decision files when the same title is created again", async () => {
    const workspace = await createWorkspace("example");
    const decisionPath = join(workspace.fsPath, ".waymark", "decisions", "ADR-001-use-postgresql.md");
    await writeFile(decisionPath, "# Existing\n", "utf8");
    const store = new DecisionStore();

    const created = await store.create(workspace, "Use PostgreSQL");

    expect(created.id).toBe("ADR-002-use-postgresql");
    await expect(readFile(decisionPath, "utf8")).resolves.toBe("# Existing\n");
  });

  it("handles malformed decision metadata safely", async () => {
    const workspace = await createWorkspace("example");
    await writeFile(join(workspace.fsPath, ".waymark", "decisions", "ADR-001-broken.md"), "# Broken\n", "utf8");
    const store = new DecisionStore();

    await expect(store.list(workspace)).resolves.toMatchObject([
      {
        title: "Broken",
        status: "Unknown",
        hasMetadataWarning: true
      }
    ]);
  });
});

describe("Project initialization compatibility", () => {
  afterEach(cleanup);

  it("loads constitution, rules, and decisions from legacy .trail project memory", async () => {
    const workspace = await createWorkspace("example", false);
    await mkdir(join(workspace.fsPath, ".trail", "rules"), { recursive: true });
    await mkdir(join(workspace.fsPath, ".trail", "decisions"), { recursive: true });
    await writeFile(join(workspace.fsPath, ".trail", "constitution.md"), "# Project Constitution\n", "utf8");
    await writeFile(
      join(workspace.fsPath, ".trail", "rules", "no-redis.md"),
      "# No Redis\n\nStatus: active\nType: architecture\n\n## Rule\n\nDo not add Redis.\n",
      "utf8"
    );
    await writeFile(
      join(workspace.fsPath, ".trail", "decisions", "ADR-001-use-postgresql.md"),
      "# ADR-001: Use PostgreSQL\n\n## Status\n\nAccepted\n",
      "utf8"
    );

    await expect(new ConstitutionStore().read(workspace)).resolves.toMatchObject({
      status: "configured",
      path: join(workspace.fsPath, ".trail", "constitution.md")
    });
    await expect(new RuleStore().list(workspace)).resolves.toMatchObject([
      {
        id: "no-redis",
        title: "No Redis"
      }
    ]);
    await expect(new DecisionStore().list(workspace)).resolves.toMatchObject([
      {
        id: "ADR-001-use-postgresql",
        title: "ADR-001: Use PostgreSQL"
      }
    ]);
  });

  it("keeps an existing project marker stable while adding source-of-truth folders", async () => {
    const workspace = await createWorkspace("example", false);
    const projectMarkerStore = new ProjectMarkerStore(() => "project-1");

    await projectMarkerStore.initialize(workspace);
    await new ConstitutionStore().create(workspace);
    await new RuleStore().create(workspace, "No Redis", "architecture");
    await new DecisionStore().create(workspace, "Use PostgreSQL");

    await expect(projectMarkerStore.read(workspace)).resolves.toMatchObject({
      status: "valid",
      project: {
        projectId: "project-1"
      }
    });
  });

  it("does not overwrite existing user files during project initialization", async () => {
    const workspace = await createWorkspace("example");
    const rulePath = join(workspace.fsPath, ".waymark", "rules", "existing.md");
    const decisionPath = join(workspace.fsPath, ".waymark", "decisions", "existing.md");
    await writeFile(rulePath, "# Existing Rule\n", "utf8");
    await writeFile(decisionPath, "# Existing Decision\n", "utf8");

    await new ProjectMarkerStore(() => "project-1").initialize(workspace);

    await expect(readFile(rulePath, "utf8")).resolves.toBe("# Existing Rule\n");
    await expect(readFile(decisionPath, "utf8")).resolves.toBe("# Existing Decision\n");
  });
});

async function createWorkspace(
  name: string,
  createWaymarkSubdirectories = true
): Promise<{ readonly name: string; readonly fsPath: string }> {
  const root = await mkdtemp(join(tmpdir(), "trail-vscode-extension-"));
  tempRoots.push(root);
  const workspacePath = join(root, name);
  await mkdir(workspacePath, { recursive: true });
  if (createWaymarkSubdirectories) {
    await mkdir(join(workspacePath, ".waymark", "rules"), { recursive: true });
    await mkdir(join(workspacePath, ".waymark", "decisions"), { recursive: true });
  }
  return {
    name,
    fsPath: workspacePath
  };
}

async function cleanup(): Promise<void> {
  await Promise.all(tempRoots.map((path) => rm(path, { recursive: true, force: true })));
  tempRoots.length = 0;
}
