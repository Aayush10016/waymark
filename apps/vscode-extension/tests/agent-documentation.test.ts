import { readFile } from "node:fs/promises";
import { join } from "node:path";

import { describe, expect, it } from "vitest";

describe("VS Code extension agent documentation", () => {
  it("documents how Codex and other coding agents should consume Waymark project truth", async () => {
    const readme = await readFile(join(process.cwd(), "README.md"), "utf8");

    expect(readme).toContain("Using Waymark with Codex, Copilot, and Other AI Agents");
    expect(readme).toContain("Read .waymark/constitution.md.");
    expect(readme).toContain("Read all active rules under .waymark/rules/.");
    expect(readme).toContain("Read relevant decisions under .waymark/decisions/.");
    expect(readme).toContain("Do not create or modify project rules unless explicitly instructed by the user.");
    expect(readme).toContain("Waymark's deterministic Guardian independently checks the resulting changed code.");
  });

  it("documents .waymark as canonical project memory with concise legacy .trail compatibility", async () => {
    const readme = await readFile(join(process.cwd(), "README.md"), "utf8");

    expect(readme).toContain("Waymark stores project rules, architectural decisions, constitution data, and project memory in:");
    expect(readme).toContain(".waymark/");
    expect(readme).toContain(".waymark/project.json");
    expect(readme).toContain(".waymark/rules/*.md");
    expect(readme).toContain("Older Waymark projects may use `.trail/`, the previous on-disk project-memory format.");
    expect(readme).toContain("migrate them to `.waymark/` with explicit user approval");
  });

  it("documents official NVIDIA onboarding without implying Waymark provides an API key", async () => {
    const readme = await readFile(join(process.cwd(), "README.md"), "utf8");

    expect(readme).toContain("https://build.nvidia.com/");
    expect(readme).toContain("https://build.nvidia.com/settings/api-keys");
    expect(readme).toContain("https://docs.api.nvidia.com/nim/reference/llm-apis");
    expect(readme).toContain("Waymark does not provide a shared API key.");
    expect(readme).toContain("[Environment]::SetEnvironmentVariable(");
    expect(readme).toContain("\"NVIDIA_API_KEY\"");
    expect(readme).toContain("Completely close VS Code.");
    expect(readme).toContain("Run `Waymark: Show AI Configuration`.");
    expect(readme).toContain("The actual API key is never displayed.");
  });

  it("describes Guardian and NVIDIA data handling accurately", async () => {
    const readme = await readFile(join(process.cwd(), "README.md"), "utf8");

    expect(readme).toContain("Waymark helps detect project-rule violations introduced by AI-assisted code changes.");
    expect(readme).toContain("Guardian detects and reports. It does not automatically remove the violating code.");
    expect(readme).toContain("Waymark does not automatically modify files merely because an AI model suggested a fix.");
    expect(readme).toContain("Automatic Guardian does not call NVIDIA.");
    expect(readme).toContain("selected/contextual code and relevant project evidence may be sent to the configured NVIDIA endpoint");
    expect(readme).not.toContain("prevents AI hallucinations");
    expect(readme).not.toContain("guarantees code correctness");
    expect(readme).not.toContain("Trail Guardian");
    expect(readme).not.toContain("Trail Project Memory");
    expect(readme).not.toContain("Trail:");
  });
});
