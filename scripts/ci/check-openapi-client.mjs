import { execFileSync } from "node:child_process";
import { readFileSync } from "node:fs";

const generatedPath = "packages/api-client/src/generated/schema.ts";
const before = readFileSync(generatedPath, "utf8");
const openapiTypescriptCli = "node_modules/openapi-typescript/bin/cli.js";

const generated = execFileSync(
  process.execPath,
  [openapiTypescriptCli, "contracts/openapi/trail-v1.yaml"],
  { encoding: "utf8" }
);

if (before !== generated) {
  throw new Error("Generated OpenAPI client is stale. Run `pnpm openapi:generate`.");
}
