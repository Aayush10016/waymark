import { readdir, readFile } from "node:fs/promises";
import { join } from "node:path";

import { describe, expect, it } from "vitest";

const forbiddenRuntimePatterns = [
  /\bXMLHttpRequest\b/,
  /\baxios\b/,
  /\bOpenAI\b/,
  /\bAnthropic\b/,
  /\bGemini\b/,
  /\bembedding\b/i,
  /\bpgvector\b/i
];

describe("VS Code extension safety boundary", () => {
  it("does not introduce unsupported network clients, backend calls, or embedding code", async () => {
    const sourceFiles = await listTypeScriptFiles(join(process.cwd(), "src"));
    const violations: string[] = [];

    for (const sourceFile of sourceFiles) {
      const content = await readFile(sourceFile, "utf8");
      for (const pattern of forbiddenRuntimePatterns) {
        if (pattern.test(content)) {
          violations.push(`${sourceFile}: ${pattern.source}`);
        }
      }
    }

    expect(violations).toEqual([]);
  });

  it("keeps AI network access isolated to the NVIDIA provider", async () => {
    const sourceFiles = await listTypeScriptFiles(join(process.cwd(), "src"));
    const violations: string[] = [];

    for (const sourceFile of sourceFiles) {
      const content = await readFile(sourceFile, "utf8");
      if (/\bfetch\s*\(/.test(content) && !sourceFile.endsWith(join("src", "ai", "nvidia-provider.ts"))) {
        violations.push(sourceFile);
      }
    }

    expect(violations).toEqual([]);
  });

  it("does not hardcode the NVIDIA API key value", async () => {
    const sourceFiles = await listTypeScriptFiles(join(process.cwd(), "src"));
    const violations: string[] = [];

    for (const sourceFile of sourceFiles) {
      const content = await readFile(sourceFile, "utf8");
      if (/Bearer\s+(?!\$\{apiKey\})[A-Za-z0-9._-]{12,}/.test(content)) {
        violations.push(sourceFile);
      }
    }

    expect(violations).toEqual([]);
  });

  it("uses child_process only inside the isolated Git client", async () => {
    const sourceFiles = await listTypeScriptFiles(join(process.cwd(), "src"));
    const violations: string[] = [];

    for (const sourceFile of sourceFiles) {
      const content = await readFile(sourceFile, "utf8");
      if (content.includes("node:child_process") && !sourceFile.endsWith(join("src", "git", "git-client.ts"))) {
        violations.push(sourceFile);
      }
    }

    expect(violations).toEqual([]);
  });

  it("does not include mutating Git operations in source", async () => {
    const sourceFiles = await listTypeScriptFiles(join(process.cwd(), "src"));
    const mutatingOperations = ["commit", "checkout", "reset", "push", "pull", "merge", "rebase", "stash", "add", "restore"];
    const violations: string[] = [];

    for (const sourceFile of sourceFiles) {
      const content = await readFile(sourceFile, "utf8");
      for (const operation of mutatingOperations) {
        if (new RegExp(`["']${operation}["']`).test(content) && !sourceFile.endsWith(join("src", "git", "git-client.ts"))) {
          violations.push(`${sourceFile}: ${operation}`);
        }
      }
    }

    expect(violations).toEqual([]);
  });
});

async function listTypeScriptFiles(directory: string): Promise<string[]> {
  const entries = await readdir(directory, { withFileTypes: true });
  const files = await Promise.all(
    entries.map(async (entry) => {
      const path = join(directory, entry.name);
      if (entry.isDirectory()) {
        return listTypeScriptFiles(path);
      }

      return entry.isFile() && path.endsWith(".ts") ? [path] : [];
    })
  );
  return files.flat();
}
