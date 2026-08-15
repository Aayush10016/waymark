import { readFileSync } from "node:fs";
import { describe, expect, it } from "vitest";
import { trailApiBaseUrl } from "../src/shared/config/api-base-url";

describe("extension backend connectivity configuration", () => {
  const manifest = JSON.parse(readFileSync("manifest/manifest.json", "utf8")) as {
    host_permissions?: string[];
  };

  it("keeps the local API base URL covered by a narrow MV3 host permission", () => {
    const apiBaseUrl = new URL(trailApiBaseUrl());
    const requiredHostPermission = `${apiBaseUrl.origin}/*`;

    expect(trailApiBaseUrl()).toBe("http://localhost:8080/api/v1");
    expect(manifest.host_permissions).toEqual([requiredHostPermission]);
  });

  it("does not request broad host access for backend connectivity", () => {
    expect(manifest.host_permissions).toEqual(["http://localhost:8080/*"]);
    expect(manifest.host_permissions?.some((permission) => permission.includes("*://"))).toBe(false);
    expect(manifest.host_permissions?.some((permission) => permission.includes("://*/"))).toBe(false);
    expect(manifest.host_permissions?.some((permission) => permission === "<all_urls>")).toBe(false);
  });
});
