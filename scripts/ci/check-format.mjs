import { readdirSync, readFileSync, statSync } from "node:fs";
import { join, relative } from "node:path";

const root = process.cwd();
const ignoredDirectories = new Set([
  ".git",
  ".idea",
  ".vscode",
  "dist",
  "node_modules",
  "target"
]);
const binaryExtensions = new Set([
  ".gif",
  ".ico",
  ".jar",
  ".jpeg",
  ".jpg",
  ".pdf",
  ".png",
  ".webp",
  ".zip"
]);

const violations = [];

function isBinaryPath(path) {
  return [...binaryExtensions].some((extension) => path.endsWith(extension));
}

function walk(directory) {
  for (const entry of readdirSync(directory, { withFileTypes: true })) {
    if (entry.isDirectory() && ignoredDirectories.has(entry.name)) {
      continue;
    }

    const absolutePath = join(directory, entry.name);
    if (entry.isDirectory()) {
      walk(absolutePath);
      continue;
    }

    if (!entry.isFile() || isBinaryPath(absolutePath)) {
      continue;
    }

    checkFile(absolutePath);
  }
}

function checkFile(absolutePath) {
  const buffer = readFileSync(absolutePath);
  if (buffer.includes(0)) {
    return;
  }

  const text = buffer.toString("utf8");
  const path = relative(root, absolutePath).replaceAll("\\", "/");

  if (text.length > 0 && !text.endsWith("\n")) {
    violations.push(`${path}: missing final newline`);
  }

  const lines = text.split(/\n/);
  lines.forEach((line, index) => {
    const normalizedLine = line.endsWith("\r") ? line.slice(0, -1) : line;
    if (/[ \t]$/.test(normalizedLine)) {
      violations.push(`${path}:${index + 1}: trailing whitespace`);
    }
  });
}

walk(root);

if (violations.length > 0) {
  console.error("Format check failed:");
  for (const violation of violations) {
    console.error(`- ${violation}`);
  }
  process.exit(1);
}

console.log("Format check passed.");

