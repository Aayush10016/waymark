import { describe, expect, it, vi } from "vitest";

import type { GuardianFileContent } from "../src/guardian/file-content-context";
import type { AutomaticGuardianBatchResult, AutomaticGuardianRunner } from "../src/guardian/automatic-guardian-runner";
import type { GuardianFinding } from "../src/guardian/models";
import type { DiagnosticCollection } from "vscode";

interface MockUri {
  readonly scheme: string;
  readonly fsPath: string;
  toString(): string;
}

interface MockDocument {
  readonly uri: MockUri;
  getText(): string;
}

const vscodeMock = vi.hoisted(() => {
  const textDocuments: MockDocument[] = [];
  const fileContents = new Map<string, string>();

  return {
    textDocuments,
    fileContents,
    createUri: (fsPath: string): MockUri => ({
      scheme: "file",
      fsPath,
      toString: () => `file://${fsPath}`
    })
  };
});

vi.mock(
  "vscode",
  () => {
    class MockRange {
      public readonly startLine: number;
      public readonly startCharacter: number;
      public readonly endLine: number;
      public readonly endCharacter: number;

      public constructor(startLine: number, startCharacter: number, endLine: number, endCharacter: number) {
        this.startLine = startLine;
        this.startCharacter = startCharacter;
        this.endLine = endLine;
        this.endCharacter = endCharacter;
      }
    }

    class MockDiagnostic {
      public source: string | undefined;
      public code: string | undefined;

      public constructor(
        public readonly range: MockRange,
        public readonly message: string,
        public readonly severity: number
      ) {}
    }

    return {
      Diagnostic: MockDiagnostic,
      DiagnosticSeverity: {
        Warning: 1,
        Information: 2
      },
      Range: MockRange,
      RelativePattern: class MockRelativePattern {
        public constructor(
          public readonly folder: unknown,
          public readonly pattern: string
        ) {}
      },
      Uri: {
        file: vscodeMock.createUri
      },
      languages: {
        createDiagnosticCollection: () => fakeDiagnosticCollection()
      },
      workspace: {
        textDocuments: vscodeMock.textDocuments,
        fs: {
          readFile: async (uri: MockUri) => {
            await Promise.resolve();
            return Buffer.from(vscodeMock.fileContents.get(uri.fsPath) ?? "");
          }
        },
        getWorkspaceFolder: (uri: MockUri) =>
          uri.fsPath.startsWith("/workspace/")
            ? {
                name: "workspace",
                uri: vscodeMock.createUri("/workspace")
              }
            : undefined,
        createFileSystemWatcher: () => fakeDisposable(),
        onDidChangeTextDocument: () => fakeDisposable(),
        onDidSaveTextDocument: () => fakeDisposable(),
        onDidCloseTextDocument: () => fakeDisposable(),
        onDidChangeWorkspaceFolders: () => fakeDisposable(),
        workspaceFolders: []
      }
    };
  }
);

describe("VsCodeAutomaticGuardian", () => {
  it("clears sidebar findings and diagnostics when a file recheck returns no findings", async () => {
    const { VsCodeAutomaticGuardian } = await import("../src/guardian/vscode-automatic-guardian");
    const uri = vscodeMock.createUri("/workspace/backend/src/main/java/ArcadeScoreController.java");
    const diagnostics = fakeDiagnosticCollection();
    const sink = new CapturingFindingSink();
    const guardian = new VsCodeAutomaticGuardian(runner(), sink, diagnostics as unknown as DiagnosticCollection);

    vscodeMock.textDocuments.splice(0, vscodeMock.textDocuments.length, document(uri, "RedisTemplate redisTemplate;"));
    await invokeCheckUris(guardian, [uri]);

    vscodeMock.textDocuments.splice(0, vscodeMock.textDocuments.length, document(uri, "class ArcadeScoreController {}"));
    await invokeCheckUris(guardian, [uri]);

    expect(sink.findings).toEqual([]);
    expect(diagnostics.deletedUris).toEqual([uri.toString()]);
    expect(diagnostics.setUris).toEqual([uri.toString()]);
  });

  it("keeps unrelated file diagnostics when one file is rechecked clean", async () => {
    const { VsCodeAutomaticGuardian } = await import("../src/guardian/vscode-automatic-guardian");
    const firstUri = vscodeMock.createUri("/workspace/backend/src/main/java/A.java");
    const secondUri = vscodeMock.createUri("/workspace/backend/src/main/java/B.java");
    const diagnostics = fakeDiagnosticCollection();
    const sink = new CapturingFindingSink();
    const guardian = new VsCodeAutomaticGuardian(runner(), sink, diagnostics as unknown as DiagnosticCollection);

    vscodeMock.textDocuments.splice(
      0,
      vscodeMock.textDocuments.length,
      document(firstUri, "RedisTemplate redisTemplate;"),
      document(secondUri, "RedisTemplate otherRedisTemplate;")
    );
    await invokeCheckUris(guardian, [firstUri, secondUri]);

    vscodeMock.textDocuments.splice(
      0,
      vscodeMock.textDocuments.length,
      document(firstUri, "class A {}"),
      document(secondUri, "RedisTemplate otherRedisTemplate;")
    );
    await invokeCheckUris(guardian, [firstUri]);

    expect(sink.findings.map((finding) => finding.codePath)).toEqual([secondUri.fsPath]);
    expect(diagnostics.deletedUris).toEqual([firstUri.toString()]);
    expect(diagnostics.activeUris()).toEqual([secondUri.toString()]);
  });
});

class ContentBasedRunner {
  public async checkFiles(files: readonly GuardianFileContent[]): Promise<AutomaticGuardianBatchResult> {
    await Promise.resolve();
    const fileResults = files.map((file) => {
      const findings = file.content.includes("RedisTemplate") ? [finding(file)] : [];
      return {
        file,
        result: {
          findings,
          checkedRules: 1,
          checkedDecisions: 0,
          checkedFiles: 1
        }
      };
    });

    return {
      fileResults,
      findings: fileResults.flatMap((fileResult) => fileResult.result.findings)
    };
  }
}

function runner(): AutomaticGuardianRunner {
  return new ContentBasedRunner() as unknown as AutomaticGuardianRunner;
}

class CapturingFindingSink {
  public findings: readonly GuardianFinding[] = [];

  public setGuardianFindings(findings: readonly GuardianFinding[]): void {
    this.findings = findings;
  }
}

function document(uri: MockUri, content: string): MockDocument {
  return {
    uri,
    getText: () => content
  };
}

function fakeDiagnosticCollection(): {
  readonly deletedUris: string[];
  readonly setUris: string[];
  set(uri: MockUri, diagnostics: readonly unknown[]): void;
  delete(uri: MockUri): void;
  dispose(): void;
  activeUris(): readonly string[];
} {
  const active = new Map<string, readonly unknown[]>();
  const deletedUris: string[] = [];
  const setUris: string[] = [];
  return {
    deletedUris,
    setUris,
    set: (uri, diagnostics) => {
      setUris.push(uri.toString());
      active.set(uri.toString(), diagnostics);
    },
    delete: (uri) => {
      deletedUris.push(uri.toString());
      active.delete(uri.toString());
    },
    dispose: () => undefined,
    activeUris: () => [...active.keys()]
  };
}

function fakeDisposable(): { dispose(): void } {
  return {
    dispose: () => undefined
  };
}

async function invokeCheckUris(guardian: unknown, uris: readonly MockUri[]): Promise<void> {
  await (guardian as { checkUris(uris: readonly MockUri[]): Promise<void> }).checkUris(uris);
}

function finding(file: GuardianFileContent): GuardianFinding {
  return {
    id: `finding:${file.filePath}`,
    severity: "WARNING",
    title: "Forbidden Redis dependency",
    message: "Redis is forbidden by project rules.",
    ruleId: "no-redis",
    ruleTitle: "Redis is not allowed",
    rulePath: "/workspace/.trail/rules/no-redis.md",
    matchedTerms: ["RedisTemplate"],
    evidence: [],
    source: "AUTOMATIC_FILE_CHANGE",
    workspaceFolder: file.workspaceFolder,
    codePath: file.filePath
  };
}
