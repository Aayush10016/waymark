import { readFile } from "node:fs/promises";
import { join } from "node:path";

import { describe, expect, it } from "vitest";

describe("VS Code extension agent documentation", () => {
  it("documents how Codex and other coding agents should consume Waymark project truth", async () => {
    const readme = await readFile(join(process.cwd(), "README.md"), "utf8");

    expect(readme).toContain("Using Waymark with Codex or other coding agents");
    expect(readme).toContain("Read `.trail/constitution.md`.");
    expect(readme).toContain("Read active `.trail/rules/*.md`.");
    expect(readme).toContain("Read relevant `.trail/decisions/*.md`.");
    expect(readme).toContain("Never invent or add new project rules without explicit user approval.");
    expect(readme).toContain("Guardian remains the deterministic local verification layer.");
  });
});
