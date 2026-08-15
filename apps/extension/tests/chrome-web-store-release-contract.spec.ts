import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { describe, expect, it } from "vitest";

const root = resolve(process.cwd(), "../..");

function readRepositoryFile(path: string): string {
  return readFileSync(resolve(root, path), "utf8");
}

describe("Chrome Web Store release contract", () => {
  it("documents package, permission, network, rollout, and rollback disclosures", () => {
    const content = readRepositoryFile("docs/release/chrome-web-store-disclosure-v1.md");

    expect(content).toContain("Package contract");
    expect(content).toContain("Permissions disclosure");
    expect(content).toContain("Network disclosure");
    expect(content).toContain("Rollout and rollback");
    expect(content).toContain("storage");
    expect(content).toContain("tabs");
    expect(content).toContain("webNavigation");
    expect(content).toContain("sidePanel");
    expect(content).toContain("alarms");
    expect(content).toContain("http://localhost:8080/*");
    expect(content).toContain("configured Trail backend API");
    expect(content).not.toContain("TODO");
    expect(content).not.toContain("FIXME");
  });

  it("keeps packaging scripts deterministic and rejects source maps or unsafe package contents", () => {
    const packageScript = readRepositoryFile("apps/extension/scripts/package-web-store.mjs");
    const validateScript = readRepositoryFile("apps/extension/scripts/validate-web-store-package.mjs");

    expect(packageScript).toContain("artifactName");
    expect(packageScript).toContain("trail-extension-${packageVersion}");
    expect(packageScript).toContain("${artifactName}.zip");
    expect(packageScript).toContain("sha256");
    expect(packageScript).toContain("files.sort");
    expect(packageScript).toContain("Source map is not allowed");
    expect(validateScript).toContain("source map found");
    expect(validateScript).toContain("remote-code-like dynamic execution");
    expect(validateScript).toContain("manifest permissions do not match the approved disclosure");
    expect(validateScript).toContain("manifest host permissions do not match the approved disclosure");
    expect(validateScript).toContain("secret-like package content");
    expect(validateScript).toContain("/sk-[A-Za-z0-9]{20,}/");
  });
});
