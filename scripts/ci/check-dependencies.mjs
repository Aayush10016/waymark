import { execFileSync } from "node:child_process";
import { existsSync } from "node:fs";

const isWindows = process.platform === "win32";
const mavenWrapper = isWindows ? "mvnw.cmd" : "./mvnw";
const pnpmCliPath = process.env.npm_execpath;

function run(command, args) {
  execFileSync(command, args, {
    stdio: "inherit"
  });
}

if (!existsSync(isWindows ? "mvnw.cmd" : "mvnw")) {
  throw new Error("Maven Wrapper is missing.");
}

if (isWindows) {
  run("cmd.exe", ["/d", "/s", "/c", mavenWrapper, "-q", "-DskipTests", "dependency:tree"]);
} else {
  run(mavenWrapper, ["-q", "-DskipTests", "dependency:tree"]);
}

if (pnpmCliPath?.includes("pnpm")) {
  run(process.execPath, [pnpmCliPath, "audit", "--audit-level", "high"]);
} else if (isWindows) {
  run("cmd.exe", ["/d", "/s", "/c", "pnpm.cmd", "audit", "--audit-level", "high"]);
} else {
  run("pnpm", ["audit", "--audit-level", "high"]);
}
