import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { describe, expect, it } from "vitest";

const auditPath = resolve(process.cwd(), "../../docs/quality/accessibility-visual-state-audit-v1.md");

function auditText(): string {
  return readFileSync(auditPath, "utf8");
}

function auditRows(): string[] {
  return auditText()
    .split("\n")
    .filter((line) => /^\|\s*\d+\s*\|/.test(line));
}

describe("accessibility and visual-state audit", () => {
  it("covers the frozen eighteen-screen experience surface", () => {
    const rows = auditRows();

    expect(rows).toHaveLength(18);
    expect(rows.join("\n")).toContain("Landing Page");
    expect(rows.join("\n")).toContain("Installation Flow");
    expect(rows.join("\n")).toContain("Permission Flow");
    expect(rows.join("\n")).toContain("First-time Onboarding");
    expect(rows.join("\n")).toContain("Empty States");
    expect(rows.join("\n")).toContain("Extension Popup");
    expect(rows.join("\n")).toContain("Active Recording State");
    expect(rows.join("\n")).toContain("Problem Detection");
    expect(rows.join("\n")).toContain("End Session Dialog");
    expect(rows.join("\n")).toContain("Problem Summary");
    expect(rows.join("\n")).toContain("Similar Problem Found");
    expect(rows.join("\n")).toContain("Ghost Replay");
    expect(rows.join("\n")).toContain("Search Experience");
    expect(rows.join("\n")).toContain("Timeline");
    expect(rows.join("\n")).toContain("Settings");
    expect(rows.join("\n")).toContain("Error States");
    expect(rows.join("\n")).toContain("Offline Mode");
    expect(rows.join("\n")).toContain("AI Processing States");
  });

  it("records every required accessibility and visual-state dimension as passing", () => {
    const text = auditText();

    expect(text).toContain("Keyboard");
    expect(text).toContain("Focus");
    expect(text).toContain("200% zoom");
    expect(text).toContain("Reduced motion");
    expect(text).toContain("Dark/light");
    expect(text).toContain("Left/right panel");
    expect(text).toContain("WCAG 2.2 AA");

    for (const row of auditRows()) {
      const passCount = row.match(/Pass/g)?.length ?? 0;
      expect(passCount).toBe(7);
    }
  });
});
