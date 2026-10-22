import { describe, expect, it } from "vitest";

import { DeterministicGuardianChecker } from "../src/guardian/guardian-checker";

import type { CodeContextPackage } from "../src/code-context/models";
import type { GitFileStatus } from "../src/git/models";
import type { GuardianContext } from "../src/guardian/models";
import type { ProjectMemorySearchResult } from "../src/memory/models";
import type { ArchitecturalDecision } from "../src/project-source/decisions";
import type { ExplicitRule } from "../src/project-source/rules";
import type { WorkspaceFolderInfo } from "../src/workspace/project-marker";

describe("DeterministicGuardianChecker", () => {
  it("uses active rules and ignores deprecated rules", () => {
    const result = checker().check(context({
      activeRules: [
        rule({ id: "no-mongodb", title: "No MongoDB", ruleText: "Do not use MongoDB." }),
        rule({ id: "old-mongo-rule", title: "Old MongoDB rule", status: "deprecated", isActive: false, ruleText: "Do not use MongoDB." })
      ],
      codeContextPackage: codePackage({
        selectedText: "const client = new MongoClient(connectionString);"
      })
    }));

    expect(result.findings).toHaveLength(1);
    expect(result.findings[0]?.ruleId).toBe("no-mongodb");
    expect(result.checkedRules).toBe(1);
  });

  it("adds accepted decisions as evidence but ignores proposed, superseded, and deprecated decisions", () => {
    const result = checker().check(context({
      activeRules: [rule({ id: "no-mongodb", title: "No MongoDB", ruleText: "Do not use MongoDB." })],
      relevantDecisions: [
        decision({ title: "MongoDB rejected for persistence", status: "Accepted" }),
        decision({ title: "MongoDB proposal", status: "Proposed" }),
        decision({ title: "MongoDB old decision", status: "Superseded" }),
        decision({ title: "MongoDB deprecated decision", status: "Deprecated" })
      ],
      codeContextPackage: codePackage({
        selectedText: "import { MongoClient } from 'mongodb';"
      })
    }));

    expect(result.findings[0]?.evidence.filter((evidence) => evidence.kind === "DECISION").map((evidence) => evidence.title))
      .toEqual(["MongoDB rejected for persistence"]);
  });

  it("detects explicit forbidden technology references", () => {
    const result = checker().check(context({
      activeRules: [rule({ title: "No MongoDB", ruleText: "Do not use MongoDB." })],
      codeContextPackage: codePackage({
        selectedText: "import { MongoClient } from 'mongodb';"
      })
    }));

    expect(result.findings).toHaveLength(1);
    expect(result.findings[0]?.matchedTerms).toContain("mongodb");
  });

  it("does not trigger when forbidden technology is not mentioned", () => {
    const result = checker().check(context({
      activeRules: [rule({ title: "No MongoDB", ruleText: "Do not use MongoDB." })],
      codeContextPackage: codePackage({
        selectedText: "const repository = new PostgresRepository(pool);"
      })
    }));

    expect(result.findings).toEqual([]);
  });

  it("detects explicit conflicting technology for required persistence technology", () => {
    const result = checker().check(context({
      activeRules: [rule({ title: "PostgreSQL persistence", ruleText: "Use PostgreSQL for persistent storage." })],
      codeContextPackage: codePackage({
        relativePath: "src/persistence/UserRepository.ts",
        fileName: "UserRepository.ts",
        selectedText: "const repository = new MongoClient(connectionString);"
      })
    }));

    expect(result.findings).toHaveLength(1);
    expect(result.findings[0]?.message).toContain("requires postgresql");
  });

  it("does not treat unrelated technology mentions as required persistence conflicts", () => {
    const result = checker().check(context({
      activeRules: [rule({ title: "PostgreSQL persistence", ruleText: "Use PostgreSQL for persistent storage." })],
      codeContextPackage: codePackage({
        relativePath: "src/telemetry/banner.ts",
        fileName: "banner.ts",
        selectedText: "const label = 'Redis conference sponsor';"
      })
    }));

    expect(result.findings).toEqual([]);
  });

  it("detects forbidden import references", () => {
    const result = checker().check(context({
      activeRules: [rule({ title: "No lodash import", ruleText: "Do not import lodash." })],
      codeContextPackage: codePackage({
        selectedText: "import debounce from 'lodash';",
        relatedImports: ["import debounce from 'lodash';"]
      })
    }));

    expect(result.findings).toHaveLength(1);
    expect(result.findings[0]?.matchedTerms).toContain("lodash");
  });

  it("detects forbidden dependency references", () => {
    const result = checker().check(context({
      activeRules: [rule({ title: "No axios dependency", ruleText: "Do not use dependency axios." })],
      codeContextPackage: codePackage({
        relativePath: "package.json",
        fileName: "package.json",
        selectedText: '"axios": "1.0.0"'
      })
    }));

    expect(result.findings).toHaveLength(1);
    expect(result.findings[0]?.matchedTerms).toContain("axios");
  });

  it("applies backend scope to backend files", () => {
    const result = checker().check(context({
      activeRules: [rule({ scope: "backend", title: "No MongoDB", ruleText: "Do not use MongoDB." })],
      codeContextPackage: codePackage({
        relativePath: "apps/backend/src/UserRepository.java",
        fileName: "UserRepository.java",
        language: "java",
        selectedText: "new MongoClient(connectionString);"
      })
    }));

    expect(result.findings).toHaveLength(1);
  });

  it("applies frontend scope to frontend files", () => {
    const result = checker().check(context({
      activeRules: [rule({ scope: "frontend", title: "No MongoDB", ruleText: "Do not use MongoDB." })],
      codeContextPackage: codePackage({
        relativePath: "apps/frontend/src/form.tsx",
        fileName: "form.tsx",
        language: "typescriptreact",
        selectedText: "const name = 'mongodb';"
      })
    }));

    expect(result.findings).toHaveLength(1);
  });

  it("does not apply unrelated or unknown scopes", () => {
    const result = checker().check(context({
      activeRules: [
        rule({ scope: "backend", title: "No MongoDB", ruleText: "Do not use MongoDB." }),
        rule({ scope: "mobile", title: "No MongoDB mobile", ruleText: "Do not use MongoDB." })
      ],
      codeContextPackage: codePackage({
        relativePath: "apps/frontend/src/form.tsx",
        fileName: "form.tsx",
        selectedText: "const name = 'mongodb';"
      })
    }));

    expect(result.findings).toEqual([]);
  });

  it("detects direct authentication implementation only when required component is bypassed", () => {
    const result = checker().check(context({
      activeRules: [rule({ title: "Authentication architecture", ruleText: "Authentication must go through AuthService." })],
      codeContextPackage: codePackage({
        selectedText: "return jwtValidator.validateToken(bearerToken);"
      })
    }));

    expect(result.findings).toHaveLength(1);
    expect(result.findings[0]?.message).toContain("AuthService");
  });

  it("does not flag generic authentication overlap when AuthService is used", () => {
    const result = checker().check(context({
      activeRules: [rule({ title: "Authentication architecture", ruleText: "Authentication must go through AuthService." })],
      codeContextPackage: codePackage({
        selectedText: "return authService.authenticate(authenticationRequest);"
      })
    }));

    expect(result.findings).toEqual([]);
  });

  it("checks current changed files without reading unrelated source files", () => {
    const result = checker().check(currentChangesContext({
      source: "CURRENT_CHANGES",
      activeRules: [rule({ scope: "backend", title: "No MongoDB", ruleText: "Do not use MongoDB." })],
      changedFiles: [status({ path: "apps/backend/src/mongodb/repository.ts", unstaged: true })]
    }));

    expect(result.findings).toHaveLength(1);
    expect(result.checkedFiles).toBe(1);
    expect(result.findings[0]?.evidence.some((evidence) => evidence.kind === "GIT_FILE")).toBe(true);
  });

  it("includes existing Project Memory evidence without creating a second retrieval path", () => {
    const result = checker().check(context({
      activeRules: [rule({ title: "No MongoDB", ruleText: "Do not use MongoDB." })],
      relevantProjectMemory: [memoryResult({ matchedTerms: ["mongodb"] })],
      codeContextPackage: codePackage({
        selectedText: "const client = new MongoClient(connectionString);"
      })
    }));

    expect(result.findings[0]?.evidence.some((evidence) => evidence.kind === "MEMORY")).toBe(true);
  });

  it("is deterministic for the same rule and code context", () => {
    const input = context({
      activeRules: [rule({ title: "No MongoDB", ruleText: "Do not use MongoDB." })],
      codeContextPackage: codePackage({
        selectedText: "const client = new MongoClient(connectionString);"
      })
    });

    expect(checker().check(input)).toEqual(checker().check(input));
  });
});

function checker(): DeterministicGuardianChecker {
  return new DeterministicGuardianChecker();
}

function context(overrides: Partial<GuardianContext> = {}): GuardianContext {
  const base = {
    workspaceFolder: workspace(),
    source: "SELECTED_CODE" as const,
    changedFiles: [],
    activeRules: [],
    relevantDecisions: [],
    relevantProjectMemory: [],
    ...overrides
  };

  return {
    ...base,
    codeContextPackage: overrides.codeContextPackage ?? codePackage()
  };
}

function currentChangesContext(
  overrides: Partial<Omit<GuardianContext, "workspaceFolder" | "codeContextPackage">> = {}
): GuardianContext {
  return {
    workspaceFolder: workspace(),
    source: "CURRENT_CHANGES",
    changedFiles: [],
    activeRules: [],
    relevantDecisions: [],
    relevantProjectMemory: [],
    ...overrides
  };
}

function codePackage(overrides: Partial<CodeContextPackage["codeContext"]> = {}): CodeContextPackage {
  return {
    codeContext: {
      workspaceFolder: workspace(),
      filePath: "/workspace/apps/backend/src/AuthController.java",
      relativePath: "apps/backend/src/AuthController.java",
      fileName: "AuthController.java",
      language: "java",
      symbolName: "createSession",
      symbolKind: "Method",
      startLine: 10,
      endLine: 14,
      selectedText: "return authService.authenticate(request);",
      selectedTextPreview: "return authService.authenticate(request);",
      isSelectionPreviewTruncated: false,
      surroundingCode: "",
      surroundingStartLine: 8,
      surroundingEndLine: 16,
      relatedImports: [],
      ...overrides
    },
    projectEvidence: [],
    gitEvidence: {
      repository: undefined,
      fileStatus: undefined,
      relevantCommits: [],
      unavailableReason: undefined
    },
    memoryResults: [],
    createdAt: "2026-08-11T00:00:00.000Z"
  };
}

function rule(overrides: Partial<ExplicitRule> = {}): ExplicitRule {
  return {
    id: "rule-1",
    title: "Rule",
    status: "active",
    type: "architecture",
    rawStatus: "active",
    rawType: "architecture",
    scope: "project-wide",
    ruleText: "Do not use MongoDB.",
    reason: "",
    source: "",
    path: "/workspace/.waymark/rules/rule.md",
    isActive: true,
    hasMetadataWarning: false,
    ...overrides
  };
}

function decision(overrides: Partial<ArchitecturalDecision> = {}): ArchitecturalDecision {
  return {
    id: "ADR-001",
    title: "Decision",
    status: "Accepted",
    rawStatus: "Accepted",
    path: "/workspace/.waymark/decisions/ADR-001.md",
    hasMetadataWarning: false,
    ...overrides
  };
}

function memoryResult(overrides: Partial<ProjectMemorySearchResult> = {}): ProjectMemorySearchResult {
  return {
    document: {
      id: "rule-memory",
      sourceType: "RULE",
      title: "No MongoDB",
      path: "/workspace/.waymark/rules/no-mongodb.md",
      content: "Do not use MongoDB.",
      metadata: {},
      updatedAt: "2026-08-11T00:00:00.000Z",
      workspaceFolder: workspace()
    },
    score: 20,
    matchedFields: ["content"],
    matchedTerms: ["mongodb"],
    ...overrides
  };
}

function status(overrides: Partial<GitFileStatus> = {}): GitFileStatus {
  return {
    path: "apps/backend/src/file.ts",
    previousPath: undefined,
    staged: false,
    unstaged: false,
    untracked: false,
    deleted: false,
    renamed: false,
    ...overrides
  };
}

function workspace(): WorkspaceFolderInfo {
  return {
    name: "trail",
    fsPath: "/workspace"
  };
}
