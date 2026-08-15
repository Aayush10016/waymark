import { readFileSync } from "node:fs";
import { describe, expect, it } from "vitest";

describe("MV3 manifest", () => {
  const manifest = JSON.parse(readFileSync("manifest/manifest.json", "utf8")) as {
    content_security_policy?: {
      extension_pages?: string;
    };
    manifest_version: number;
    permissions?: string[];
    content_scripts?: unknown;
    externally_connectable?: unknown;
    host_permissions?: string[];
  };

  it("uses Manifest V3 without content scripts or external messaging", () => {
    expect(manifest.manifest_version).toBe(3);
    expect(manifest.content_scripts).toBeUndefined();
    expect(manifest.externally_connectable).toBeUndefined();
  });

  it("requests only approved MVP permissions", () => {
    expect(manifest.permissions?.toSorted()).toEqual([
      "alarms",
      "sidePanel",
      "storage",
      "tabs",
      "webNavigation"
    ]);
  });

  it("declares only the local backend host permission required for extension API fetches", () => {
    expect(manifest.host_permissions).toEqual(["http://localhost:8080/*"]);
    expect(manifest.host_permissions).not.toContain("<all_urls>");
    expect(manifest.host_permissions).not.toContain("http://*/*");
    expect(manifest.host_permissions).not.toContain("https://*/*");
    expect(manifest.host_permissions).not.toContain("*://*/*");
  });

  it("uses a restrictive extension page CSP with no remote code or unsafe evaluation", () => {
    expect(manifest.content_security_policy?.extension_pages).toBe(
      "script-src 'self'; object-src 'self'; base-uri 'self'"
    );
    expect(manifest.content_security_policy?.extension_pages).not.toContain("unsafe-inline");
    expect(manifest.content_security_policy?.extension_pages).not.toContain("unsafe-eval");
    expect(manifest.content_security_policy?.extension_pages).not.toContain("http:");
    expect(manifest.content_security_policy?.extension_pages).not.toContain("https:");
  });
});
